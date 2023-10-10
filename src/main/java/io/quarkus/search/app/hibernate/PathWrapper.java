package io.quarkus.search.app.hibernate;

import java.nio.file.Path;

// Workaround for https://hibernate.atlassian.net/browse/HSEARCH-4988
public record PathWrapper(Path value) {
}
