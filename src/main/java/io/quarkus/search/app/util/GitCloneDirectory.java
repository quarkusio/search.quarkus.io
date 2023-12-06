package io.quarkus.search.app.util;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import io.quarkus.search.app.entity.Language;
import io.quarkus.search.app.quarkusio.QuarkusIO;

import io.quarkus.logging.Log;

import org.hibernate.search.util.common.impl.Closer;
import org.hibernate.search.util.common.impl.SuppressingCloser;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevTree;

public class GitCloneDirectory implements Closeable {
    private final Git git;

    private final CloseableDirectory directory;
    private final String pagesBranch;
    protected RevTree pagesTree;

    public static GitCloneDirectory mainRepository(URI gitUri, List<String> branches)
            throws IOException, GitAPIException {
        return create(gitUri, branches, "quarkus.io", QuarkusIO.PAGES_BRANCH);
    }

    public static GitCloneDirectory localizedRepository(Language language, URI gitUri, List<String> branches) {
        return create(gitUri, branches, language.code + ".quarkus.io", QuarkusIO.LOCALIZED_PAGES_BRANCH);
    }

    private static GitCloneDirectory create(URI gitUri, List<String> branches, String prefix, String branch) {
        Log.infof("Fetching from %s.", gitUri);
        CloseableDirectory directory = null;
        Git git = null;
        try {
            directory = CloseableDirectory.temp(prefix);
            git = createGitClone(gitUri, branches, directory);

            return new GitCloneDirectory(git, directory, branch);
        } catch (IOException | GitAPIException e) {
            new SuppressingCloser(e)
                    .pushAll(git)
                    .pushAll(directory);
            throw new IllegalStateException(
                    "Failed to clone git repository '" + gitUri + "': " + e.getMessage(), e);
        }
    }

    private GitCloneDirectory(Git git, CloseableDirectory directory, String pagesBranch) {
        this.git = git;
        this.directory = directory;
        this.pagesBranch = pagesBranch;
    }

    public Git git() {
        return git;
    }

    public CloseableDirectory directory() {
        return directory;
    }

    public RevTree pagesTree() {
        if (this.pagesTree == null) {
            try {
                this.pagesTree = GitUtils.firstExistingRevTree(git().getRepository(), "origin/" + pagesBranch);
            } catch (IOException e) {
                throw new RuntimeException("Unable to locate pages branch: " + pagesBranch, e);
            }
        }
        return pagesTree;
    }

    @Override
    public void close() throws IOException {
        try (Closer<IOException> closer = new Closer<>()) {
            closer.push(Git::close, git);
            closer.push(CloseableDirectory::close, directory);
        }
    }

    private static Git createGitClone(URI gitUri, List<String> branches, CloseableDirectory directory)
            throws GitAPIException {
        return Git.cloneRepository()
                .setURI(gitUri.toString())
                .setDirectory(directory.path().toFile())
                .setDepth(1)
                .setNoTags()
                .setBranch(branches.get(0))
                .setBranchesToClone(branches.stream().map(b -> "refs/heads/" + b).toList())
                .setProgressMonitor(new TextProgressMonitor())
                // Unfortunately sparse checkouts are not supported: https://www.eclipse.org/forums/index.php/t/1094825/
                .call();
    }
}
