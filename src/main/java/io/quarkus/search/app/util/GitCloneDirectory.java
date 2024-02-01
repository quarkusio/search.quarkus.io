package io.quarkus.search.app.util;

import static io.quarkus.search.app.util.GitUtils.revTree;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.quarkus.search.app.fetching.LoggerProgressMonitor;

import io.quarkus.logging.Log;

import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.impl.SuppressingCloser;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.jboss.logging.Logger;

public class GitCloneDirectory implements Closeable {

    public static GitCloneDirectory clone(URI gitUri, Path directory, Branches branches) {
        var details = new Details(directory, branches);
        Log.infof("Cloning into '%s' from '%s'.", directory, gitUri);
        Git git = null;
        String remoteName = ORDERED_REMOTES.get(0);
        try {
            git = Git.cloneRepository()
                    .setURI(gitUri.toString())
                    .setDirectory(directory.toFile())
                    .setRemote(remoteName)
                    .setDepth(1)
                    .setNoTags()
                    .setBranch(branches.sources())
                    .setBranchesToClone(branches.asRefList())
                    .setProgressMonitor(LoggerProgressMonitor.create(log, "Cloning " + gitUri + ": "))
                    // Unfortunately sparse checkouts are not supported: https://www.eclipse.org/forums/index.php/t/1094825/
                    .call();
            return new GitCloneDirectory(git, details, remoteName);
        } catch (RuntimeException | GitAPIException e) {
            new SuppressingCloser(e).push(git);
            throw new IllegalStateException(
                    "Failed to clone git repository into '%s' from '%s': %s".formatted(directory, gitUri, e.getMessage()),
                    e);
        }
    }

    public static GitCloneDirectory openAndUpdate(Path directory, Branches branches) {
        var details = new Details(directory, branches);
        return details.openAndUpdate();
    }

    private static final Logger log = Logger.getLogger(GitCloneDirectory.class);
    private static final List<String> ORDERED_REMOTES = Arrays.asList("upstream", "origin");

    private GitCloneDirectory root;
    private final Git git;
    private final Details details;
    private final String remoteName;
    private RevTree pagesTree;
    private RevTree sourcesTree;
    private RevTree sourcesTranslationTree;

    public GitCloneDirectory(Git git, Details details, String remoteName) {
        this.git = git;
        this.details = details;
        this.remoteName = remoteName;
    }

    public GitCloneDirectory root(GitCloneDirectory root) {
        this.root = root;
        if (root != null) {
            Optional<ObjectId> objectId = currentUpstreamSubmoduleSourcesHash();
            // if the hash is missing it means we are working with test repositories, and that is handled elsewhere
            if (objectId.isPresent()) {
                // need to make sure that the root repository will have the rev we need when we'd request a source file,
                // or when the commit happened etc.
                String hash = objectId.get().getName();
                try {
                    root.git().fetch().setRemote(root.remoteName).setRefSpecs(hash)
                            .setProgressMonitor(
                                    LoggerProgressMonitor.create(log, "Fetching from " + root.remoteName + " " + hash + ": "))
                            .call();
                } catch (GitAPIException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return this;
    }

    public Details details() {
        return details;
    }

    public Git git() {
        return git;
    }

    public Details directory() {
        return details;
    }

    public RevTree pagesTree() {
        if (this.pagesTree == null) {
            this.pagesTree = treeForBranch(this, details.branches().pages());
        }
        return pagesTree;
    }

    public RevTree sourcesTranslationTree() {
        if (this.sourcesTranslationTree == null && root != null) {
            this.sourcesTranslationTree = treeForBranch(this, details.branches().sources());
        }
        return sourcesTranslationTree;
    }

    public Stream<String> sourcesFileStream(String path, Predicate<String> filenameFilter) {
        GitCloneDirectory cloneDirectory = root == null ? this : root;

        return GitUtils.fileStream(cloneDirectory.git().getRepository(), sourcesTree(), path, filenameFilter);
    }

    private static RevTree treeForBranch(GitCloneDirectory cloneDirectory, String branch) {
        try {
            String ref;
            if (cloneDirectory.remoteName == null) {
                ref = branch;
            } else {
                Map<String, Ref> stringRefMap = cloneDirectory.git.lsRemote().setRemote(cloneDirectory.remoteName)
                        .callAsMap();
                ref = stringRefMap.get("refs/heads/" + branch).getObjectId().name();
            }
            return GitUtils.firstExistingRevTree(cloneDirectory.git.getRepository(), ref);
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Unable to locate branch: " + cloneDirectory.details.branches.pages(), e);
        }
    }

    public InputStream sourcesFile(String filename) {
        GitCloneDirectory cloneDirectory = root == null ? this : root;
        return GitUtils.file(cloneDirectory.git().getRepository(), sourcesTree(), filename);
    }

    private RevTree sourcesTree() {
        if (this.sourcesTree == null) {
            if (root == null) {
                this.sourcesTree = treeForBranch(this, this.details.branches().sources());
            } else {
                Optional<ObjectId> rev = currentUpstreamSubmoduleSourcesHash();
                if (rev.isEmpty()) {
                    // not pointing to the upstream repo; no submodule; most likely we are in tests
                    // hence just use the latest
                    this.sourcesTree = treeForBranch(root, root.details.branches().sources());
                } else {
                    this.sourcesTree = revTree(root.git().getRepository(), rev.get());
                }
            }

        }
        return sourcesTree;
    }

    public ObjectId currentSourcesLatestHash() {
        try {
            return this.git().getRepository().resolve(
                    (this.remoteName == null ? "" : this.remoteName + "/") +
                            details().branches().sources());
        } catch (IOException e) {
            throw new RuntimeException("Unable to resolve current sources branch latest hash id", e);
        }
    }

    /**
     * @return If repository has an upstream git submodule, we'll return the hash of the latest commit the module currently
     *         points to;
     *         or an empty optional otherwise (no submodules)
     */
    public Optional<ObjectId> currentUpstreamSubmoduleSourcesHash() {
        try {
            Map<String, SubmoduleStatus> statusMap = git.submoduleStatus().call();
            return Optional.ofNullable(statusMap.get("upstream")).map(SubmoduleStatus::getIndexId);
        } catch (GitAPIException e) {
            throw new RuntimeException("Failed to list git submodule status for a repository: " + git.getRepository()
                    .getRemoteNames() + ". " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        try (Closer<IOException> closer = new Closer<>()) {
            closer.push(Git::close, git);
            pagesTree = null;
        }
    }

    @Override
    public String toString() {
        return "GitCloneDirectory{" +
                "git=" + git +
                ", directory=" + details +
                '}';
    }

    public record Details(Path directory, Branches branches) {
        public GitCloneDirectory openAndUpdate() {
            Log.infof("Opening and updating '%s'.", directory);
            Git git = null;
            try {
                git = Git.open(directory.toFile());
                String remoteName = inferRemoteName(git);
                if (remoteName != null) {
                    update(git, remoteName);
                }
                // Else there's nowhere to pull from, so we don't even try.
                return new GitCloneDirectory(git, this, remoteName);
            } catch (IOException e) {
                new SuppressingCloser(e).push(git);
                throw new IllegalStateException("Wasn't able to open repository '%s': '%s".formatted(directory, e.getMessage()),
                        e);
            }
        }

        private void update(Git git, String remoteName) {
            try {
                // fetch remote branches to make sure we'll use up-to-date data
                git.fetch()
                        .setRemote(remoteName)
                        .setRefSpecs(branches.asRefArray())
                        .setProgressMonitor(LoggerProgressMonitor.create(log,
                                "Fetching into '" + directory + "' (" + branches.pages() + "): "))
                        .call();
            } catch (RuntimeException | GitAPIException e) {
                new SuppressingCloser(e).push(git);
                throw new IllegalStateException(
                        "Wasn't able to pull changes for branch '%s' in repository '%s': '%s".formatted(branches,
                                directory,
                                e.getMessage()),
                        e);
            }
        }

        private String inferRemoteName(Git git) {
            Set<String> remotes = git.getRepository().getRemoteNames();
            if (remotes.isEmpty()) {
                return null;
            }
            for (String name : ORDERED_REMOTES) {
                if (remotes.contains(name)) {
                    return name;
                }
            }
            log.warn(
                    "Wasn't able to find any of the default/expected remotes (%s) so a random existing one will be picked. Indexing results are not guaranteed to be correct."
                            .formatted(ORDERED_REMOTES));
            // If at this point we still haven't figured out the remote ... then we probably are going to fail anyway,
            //  but let's give it one more chance and just pick any of the remotes at random.
            return remotes.iterator().next();
        }
    }

    public record Branches(String sources, String pages) {
        public List<String> asRefList() {
            return List.of("refs/heads/" + sources, "refs/heads/" + pages);
        }

        public String[] asRefArray() {
            return asRefList().toArray(String[]::new);
        }
    }
}
