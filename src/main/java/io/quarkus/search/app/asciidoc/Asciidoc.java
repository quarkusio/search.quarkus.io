package io.quarkus.search.app.asciidoc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

public final class Asciidoc {
    private Asciidoc() {
    }

    public static void parse(Path path, Consumer<String> titleHandler,
            Map<String, Consumer<String>> attributeHandlers) {
        try (var lines = Files.lines(path)) {
            lines.forEach(line -> {
                if (line.startsWith("=")) {
                    if (!line.startsWith("==")) {
                        titleHandler.accept(line.substring(1).trim());
                    }
                } else if (line.startsWith(":")) {
                    for (var entry : attributeHandlers.entrySet()) {
                        String prefix = ":" + entry.getKey() + ": ";
                        if (line.startsWith(prefix)) {
                            entry.getValue().accept(line.substring(prefix.length()).trim());
                        }
                    }
                }
            });
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to process file at path " + path + ": " + e.getMessage(), e);
        }
    }
}
