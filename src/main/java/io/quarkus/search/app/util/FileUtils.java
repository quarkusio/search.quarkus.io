package io.quarkus.search.app.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public final class FileUtils {

    private FileUtils() {
    }

    public static void zip(Path sourceDir, Path targetFile) throws IOException {
        try (FileSystem targetFs = FileSystems.newFileSystem(targetFile,
                Map.of("create", "true"))) {
            copyRecursively(sourceDir, targetFs.getRootDirectories().iterator().next());
        }
    }

    public static void unzip(Path sourceFile, Path targetDir) throws IOException {
        try (FileSystem sourceFs = FileSystems.newFileSystem(sourceFile)) {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            copyRecursively(sourceFs.getRootDirectories().iterator().next(), targetDir);
        }
    }

    public static void untar(Path sourceFile, Path targetDir) throws IOException {
        try (InputStream fi = Files.newInputStream(sourceFile);
                InputStream bi = new BufferedInputStream(fi);
                ArchiveInputStream<TarArchiveEntry> archive = new TarArchiveInputStream(bi)) {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            TarArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                Path extractTo = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(extractTo);
                } else {
                    Files.copy(archive, extractTo);
                }
            }
        }
    }

    static void copyRecursively(Path source, Path target, CopyOption... options) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.copy(file, target.resolve(source.relativize(file).toString()), options);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
