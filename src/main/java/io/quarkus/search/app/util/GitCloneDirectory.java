package io.quarkus.search.app.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import io.quarkus.search.app.fetching.FetchingService;
import io.quarkus.search.app.fetching.LoggerProgressMonitor;

import io.quarkus.logging.Log;

import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.impl.SuppressingCloser;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevTree;
import org.jboss.logging.Logger;

public class GitCloneDirectory implements Closeable {

    private static final Logger log = Logger.getLogger(GitCloneDirectory.class);
    private static final List<String> ORDERED_REMOTES = Arrays.asList("origin", "upstream");
    private final Git git;

    private final GitDirectoryDetails directory;
    private RevTree pagesTree;

    public GitCloneDirectory(Git git, GitDirectoryDetails directory) {
        this.git = git;
        this.directory = directory;
    }

    public GitCloneDirectory(Git git, Path path, String pages) {
        this(git, new GitDirectoryDetails(path, pages));
    }

    public Git git() {
        return git;
    }

    public Path resolve(String other) {
        return directory.directory().resolve(other);
    }

    public GitDirectoryDetails directory() {
        return directory;
    }

    public Path resolve(Path other) {
        return directory.directory().resolve(other);
    }

    public RevTree pagesTree() {
        if (this.pagesTree == null) {
            try {
                String remote = null;
                Set<String> remotes = git.getRepository().getRemoteNames();
                if (remotes.isEmpty()) {
                    remote = "";
                } else {
                    for (String name : ORDERED_REMOTES) {
                        if (remotes.contains(name)) {
                            remote = name + "/";
                        }
                    }
                    if (remote == null) {
                        log.warn(
                                "Wasn't able to find any of the default/expected remotes (%s) so a random existing one will be picked. Indexing results are not guaranteed to be correct."
                                        .formatted(ORDERED_REMOTES));
                        // If at this point we still haven't figured out the remote ... then we probably are going to fail anyway,
                        //  but let's give it one more chance and just pick any of the remotes at random.
                        remote = remotes.iterator().next() + "/";
                    }
                }

                this.pagesTree = GitUtils.firstExistingRevTree(git.getRepository(), remote + directory.pagesBranch());
            } catch (IOException e) {
                throw new RuntimeException("Unable to locate pages branch: " + directory.pagesBranch(), e);
            }
        }
        return pagesTree;
    }

    public GitCloneDirectory update(FetchingService.Branches branches) {
        directory.pull(git, branches);
        return this;
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
                ", directory=" + directory +
                '}';
    }

    public record GitDirectoryDetails(Path directory, String pagesBranch) {
        public GitCloneDirectory open() throws IOException {
            return new GitCloneDirectory(Git.open(directory.toFile()), this);
        }

        public GitCloneDirectory pull(FetchingService.Branches branches) {
            Log.infof("Pulling changes for '%s'.", directory);
            Git git = null;
            try {
                git = Git.open(directory.toFile());
                pull(git, branches);
                return new GitCloneDirectory(git, this);
            } catch (IOException e) {
                new SuppressingCloser(e).push(git);
                throw new IllegalStateException("Wasn't able to open repository '%s': '%s".formatted(directory, e.getMessage()),
                        e);
            }
        }

        private void pull(Git git, FetchingService.Branches branches) {
            try {
                Log.infof("Pulling changes for sources branch of '%s':'%s'.", directory, branches.sources());
                // just to make sure we are in the correct branch:
                git.checkout().setName(branches.sources()).call();
                // pull sources branch
                git.pull()
                        .setProgressMonitor(LoggerProgressMonitor.create(log,
                                "Pulling into '" + directory + "' (" + branches.sources() + "): "))
                        .call();
                // fetch used branches
                git.fetch().setForceUpdate(true)
                        .setProgressMonitor(LoggerProgressMonitor.create(log,
                                "Fetching into '" + directory + "' (" + branches.sources() + "): "))
                        .setRefSpecs(branches.asRefArray())
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

        public GitCloneDirectory clone(URI gitUri, FetchingService.Branches branches) {
            Log.infof("Cloning into '%s' from '%s'.", directory, gitUri);
            Git git = null;
            try {
                git = Git.cloneRepository()
                        .setURI(gitUri.toString())
                        .setDirectory(directory.toFile())
                        .setDepth(1)
                        .setNoTags()
                        .setBranch(branches.sources())
                        .setBranchesToClone(branches.asRefList())
                        .setProgressMonitor(LoggerProgressMonitor.create(log, "Cloning " + gitUri + ": "))
                        // Unfortunately sparse checkouts are not supported: https://www.eclipse.org/forums/index.php/t/1094825/
                        .call();
                return new GitCloneDirectory(git, this);
            } catch (RuntimeException | GitAPIException e) {
                new SuppressingCloser(e).push(git);
                throw new IllegalStateException(
                        "Failed to clone git repository into '%s' from '%s': %s".formatted(directory, gitUri, e.getMessage()),
                        e);
            }
        }
    }
}
