package io.quarkus.search.app.hibernate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import io.quarkus.search.app.entity.Guide;
import io.quarkus.search.app.quarkiverseio.QuarkiverseIO;
import io.quarkus.search.app.quarkusio.QuarkusIO;

public class QuarkusIOLoadingContext {

    private final Iterator<Guide> guides;
    private final Stream<Guide> guideStream;

    public static QuarkusIOLoadingContext of(QuarkusIO quarkusIO, QuarkiverseIO quarkiverseIO) throws IOException {
        return new QuarkusIOLoadingContext(Stream.concat(quarkusIO.guides(), quarkiverseIO.guides()));
    }

    QuarkusIOLoadingContext(Stream<Guide> guides) {
        this.guideStream = guides;
        this.guides = guides.iterator();
    }

    public List<Guide> nextBatch(int batchSize) {
        List<Guide> list = new ArrayList<>();
        for (int i = 0; guides.hasNext() && i < batchSize; i++) {
            list.add(guides.next());
        }
        if (!guides.hasNext()) {
            // This guide stream is a concatenated, where one of the streams is "file-based" and such stream requires to be closed.
            // Once there are no more guides in the stream:
            // we are closing the concatenated stream which will in turn propagate this to streams it is created from.
            // Which eventually will result in file-stream releasing its resources.
            guideStream.close();
        }
        return list;
    }
}
