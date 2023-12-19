package io.quarkus.search.app.util;

import java.io.Closeable;
import java.io.IOException;

import org.hibernate.search.util.common.impl.Closer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevTree;

public class GitCloneDirectory implements Closeable {
    private final Git git;

    private final CloseableDirectory directory;
    private final String pagesBranch;
    private RevTree pagesTree;

    public GitCloneDirectory(Git git, CloseableDirectory directory, String pagesBranch) {
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

}
