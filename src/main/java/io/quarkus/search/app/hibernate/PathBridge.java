package io.quarkus.search.app.hibernate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;

// TODO It's not reasonable to put the full content of a (potentially large) text file in memory
//  See https://hibernate.atlassian.net/browse/HSEARCH-4975
public class PathBridge implements ValueBridge<PathWrapper, String> {
    @Override
    public String toIndexedValue(PathWrapper path, ValueBridgeToIndexedValueContext context) {
        try {
            return Files.readString(path.value());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read '" + path + "' for indexing: " + e.getMessage(), e);
        }
    }
}
