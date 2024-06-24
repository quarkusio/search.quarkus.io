package io.quarkus.search.app.hibernate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.quarkusio.QuarkusIO;

public class QuarkusIOLoadingContext {

    private final Iterator<Guide> guides;

    public static QuarkusIOLoadingContext of(QuarkusIO quarkusIO) throws IOException {
        return new QuarkusIOLoadingContext(quarkusIO.guides());
    }

    QuarkusIOLoadingContext(Stream<Guide> guides) {
        this.guides = guides.iterator();
    }

    public int size() {
        // we don't know the size, so progress reporting will be incorrect :(
        return -1;
    }

    public synchronized List<Guide> nextBatch(int batchSize) {
        List<Guide> list = new ArrayList<>();
        for (int i = 0; guides.hasNext() && i < batchSize; i++) {
            list.add(guides.next());
        }
        return list;
    }
}
