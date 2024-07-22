package io.quarkus.search.app.indexing;

import java.util.concurrent.CompletableFuture;

import io.quarkus.logging.Log;

import org.hibernate.search.engine.reporting.impl.LogFailureHandler;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingFailureContext;
import org.hibernate.search.mapper.pojo.massindexing.MassIndexingFailureHandler;
import org.hibernate.search.mapper.pojo.massindexing.impl.PojoMassIndexingDelegatingFailureHandler;

class FailFastMassIndexingFailureHandler implements MassIndexingFailureHandler {
    private final MassIndexingFailureHandler delegate = new PojoMassIndexingDelegatingFailureHandler(new LogFailureHandler());

    volatile boolean failed = false;
    volatile CompletableFuture<?> future;

    @Override
    public void handle(MassIndexingFailureContext context) {
        delegate.handle(context);
        failed = true;
        if (future != null) {
            abort();
        }
    }

    public void init(CompletableFuture<?> future) {
        this.future = future;
        if (failed) {
            abort();
        }
    }

    private void abort() {
        Log.error("Aborting mass indexing to fail fast.");
        future.cancel(true);
    }
}
