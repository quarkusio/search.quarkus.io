package io.quarkus.search.app.hibernate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.hibernate.search.mapper.pojo.bridge.ValueBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.ValueBridgeToIndexedValueContext;

// TODO It's not reasonable to put the full content of a (potentially large) text file in memory
//  See https://hibernate.atlassian.net/browse/HSEARCH-4975
public class InputProviderHtmlBodyTextBridge implements ValueBridge<InputProvider, String> {
    @Override
    public String toIndexedValue(InputProvider provider, ValueBridgeToIndexedValueContext context) {
        try (var in = provider.open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to read '" + provider + "' for indexing: " + e.getMessage(), e);
        }
    }
}
