package io.quarkus.search.app.testsupport;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import org.jboss.logging.Logger;

import org.eclipse.jgit.util.SystemReader;

public final class GitTestUtils {

    private static final Logger LOG = Logger.getLogger(GitTestUtils.class);

    private GitTestUtils() {
    }

    public static void cleanGitUserConfig() {
        try {
            SystemReader.getInstance().getUserConfig().clear();
        } catch (Exception e) {
            LOG.warn("Unable to clear the Git user config");
        }
    }
}
