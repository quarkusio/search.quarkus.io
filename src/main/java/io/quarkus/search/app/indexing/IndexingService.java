package io.quarkus.search.app.indexing;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.FixedDemandPacer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

import org.hibernate.Session;
import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.util.common.impl.Closer;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.search.app.fetching.FetchingService;
import io.quarkus.search.app.fetching.QuarkusIO;
import io.quarkus.vertx.http.ManagementInterface;

@ApplicationScoped
public class IndexingService {
    private static final int INDEXING_BATCH_SIZE = 50;

    @Inject
    SearchMapping searchMapping;

    @Inject
    Session session;

    @Inject
    FetchingService fetchingService;

    @Inject
    IndexingConfig indexingConfig;

    private final AtomicBoolean reindexingInProgress = new AtomicBoolean();

    void registerManagementRoutes(@Observes ManagementInterface mi) {
        mi.router().get("/reindex")
                .blockingHandler(rc -> {
                    reindex();
                    rc.end("Success");
                });
    }

    void indexOnStartup(@Observes StartupEvent ev) {
        if (!indexingConfig.onStartup().enabled()) {
            Log.infof("Not reindexing on startup (disabled)");
            return;
        }
        Log.infof("Reindexing on startup");
        var waitInterval = indexingConfig.onStartup().waitInterval();
        // https://smallrye.io/smallrye-mutiny/2.0.0/guides/polling/#how-to-use-polling
        Multi.createBy().repeating()
                .supplier(this::isSearchBackendAccessible)
                .until(backendAccessible -> backendAccessible)
                .onItem().invoke(() -> {
                    Log.infof("Search backend is not reachable yet, waiting...");
                })
                .onCompletion().call(() -> Uni.createFrom()
                        .item(() -> {
                            reindex();
                            return null;
                        })
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
                // https://smallrye.io/smallrye-mutiny/2.5.1/guides/controlling-demand/#pacing-the-demand
                .paceDemand().on(Infrastructure.getDefaultWorkerPool())
                .using(new FixedDemandPacer(1L, waitInterval))
                .onFailure().invoke(t -> Log.errorf("Reindexing on startup failed: " + t.getMessage(), t))
                // We don't care about the result, we just want this to run.
                .subscribe().with(ignored -> {
                });
    }

    // https://smallrye.io/smallrye-mutiny/2.0.0/guides/delaying-events/#throttling-a-multi
    private static <T> Multi<T> throttle(Multi<T> source, Duration waitInterval) {
        Multi<Long> ticks = Multi.createFrom().ticks().every(waitInterval)
                .onOverflow().drop();
        return Multi.createBy().combining().streams(ticks, source)
                .using((x, item) -> item);
    }

    private boolean isSearchBackendAccessible() {
        try {
            searchMapping.backend().unwrap(ElasticsearchBackend.class).client(RestClient.class)
                    .performRequest(new Request("GET", "/"));
            return true;
        } catch (IOException e) {
            Log.debug("Caught exception when testing whether the search backend is reachable", e);
            return false;
        }
    }

    @ActivateRequestContext
    protected void reindex() {
        // Reindexing requires exclusive access to the DB/indexes
        if (!reindexingInProgress.compareAndSet(false, true)) {
            throw new ReindexingAlreadyInProgressException();
        }
        try {
            Log.info("Creating missing indexes (if any)");
            searchMapping.scope(Object.class).schemaManager().createIfMissing();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to create missing indexes: " + e.getMessage(), e);
        }
        try {
            indexAll();
        } finally {
            reindexingInProgress.set(false);
        }
    }

    private static class ReindexingAlreadyInProgressException extends RuntimeException {
        ReindexingAlreadyInProgressException() {
            super("Reindexing is already in progress and cannot be started at this moment");
        }
    }

    private void indexAll() {
        Log.info("Indexing...");
        try (Rollover rollover = Rollover.start(searchMapping);
                Closer<IOException> closer = new Closer<>()) {
            // We don't use the database for searching, so let's make sure to clear it at the end.
            closer.push(IndexingService::clearDatabaseWithoutIndexes, this);

            try (QuarkusIO quarkusIO = fetchingService.fetchQuarkusIo()) {
                indexQuarkusIo(quarkusIO);
            }

            // Refresh BEFORE committing the rollover,
            // so that the new indexes are fully refreshed
            // as soon as we switch the aliases.
            Log.info("Refreshing indexes...");
            searchMapping.scope(Object.class).workspace().refresh();

            rollover.commit();
            Log.info("Indexing success");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to index data: " + e.getMessage(), e);
        }
    }

    private void indexQuarkusIo(QuarkusIO quarkusIO) throws IOException {
        Log.info("Indexing quarkus.io...");
        try (var guideStream = quarkusIO.guides()) {
            batchIndex(guideStream.iterator());
        }
    }

    private <T> void batchIndex(Iterator<T> docIterator) {
        QuarkusTransaction.begin();
        Throwable failure = null;
        int i = 0;
        try {
            while (docIterator.hasNext()) {
                T doc = docIterator.next();
                session.persist(doc);

                ++i;
                if (i % INDEXING_BATCH_SIZE == 0) {
                    QuarkusTransaction.commit();
                    Log.infof("Indexed %d documents...", i);
                    QuarkusTransaction.begin();
                }
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            if (failure == null) {
                QuarkusTransaction.commit();
                Log.infof("Indexed %d documents.", i);
            } else {
                try {
                    QuarkusTransaction.rollback();
                } catch (Throwable t) {
                    failure.addSuppressed(t);
                }
            }
        }
    }

    private void clearDatabaseWithoutIndexes() {
        try {
            session.getSessionFactory().getSchemaManager().truncateMappedObjects();
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to clear the database after indexing: " + e.getMessage(), e);
        }
    }

}
