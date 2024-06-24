package io.quarkus.search.app.indexing;

import java.io.IOException;
import java.util.stream.Stream;

import io.quarkus.search.app.entity.Guide;

public interface IndexableGuides {
    Stream<Guide> guides() throws IOException;
}
