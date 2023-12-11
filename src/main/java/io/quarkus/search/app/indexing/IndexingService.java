package io.quarkus.search.app.indexing;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.search.app.fetching.FetchingService;
import io.quarkus.search.app.quarkusio.QuarkusIO;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.http.ManagementInterface;

import org.hibernate.Session;
import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.util.common.impl.Closer;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.FixedDemandPacer;

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
                .subscribe().with(
                        // We don't care about the items, we just want this to run.
                        ignored -> {
                        },
                        t -> Log.errorf(t, "Reindexing on startup failed: %s", t.getMessage()));
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
            createIndexes();
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

    private void createIndexes() {
        try {
            Log.info("Creating missing indexes");
            searchMapping.scope(Object.class).schemaManager().createIfMissing();
        } catch (RuntimeException e) {
            Log.error("Creating missing indexes failed; will attempt to recover mismatched aliases...", e);
            // This may happen if aliases were left in a stale state by a previous pod
            // that failed to get ready in time, but is potentially still indexing.
            // Ideally we'd just tell Kubernetes that a previous failed pod
            // must be killed completely before rolling out a new one,
            // so that it gets the chance to get things back in order,
            // but I don't know how to do this without having Kubernetes
            // also kill previous *ready* pods, which would completely prevent zero-downtime rollouts.
            // So, we'll just do this and accept that things will go wrong
            // (hopefully just a temporary downtime)
            // if we do two rollouts too close to each other.
            try {
                if (Rollover.recoverInconsistentAliases(searchMapping)) {
                    Log.info("Creating missing indexes after aliases were recovered");
                    searchMapping.scope(Object.class).schemaManager().createIfMissing();
                } else {
                    throw e;
                }
            } catch (RuntimeException e2) {
                e.addSuppressed(e2);
                throw new IllegalStateException("Failed to create indexes: " + e.getMessage(), e);
            }
        }
    }

    private void indexAll() {
        Log.info("Indexing...");
        try (Rollover rollover = Rollover.start(searchMapping);
                Closer<IOException> closer = new Closer<>()) {
            // Reset the database before we start
            clearDatabaseWithoutIndexes();

            try (QuarkusIO quarkusIO = fetchingService.fetchQuarkusIo()) {
                indexQuarkusIo(quarkusIO);
            }

            // We don't use the database for searching,
            // so let's make sure to clear it after we're done indexing.
            closer.push(IndexingService::clearDatabaseWithoutIndexes, this);

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
                try {
                    Log.tracef("About to persist: %s", doc);
                    session.persist(doc);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to persist '%s': %s".formatted(doc, e.getMessage()), e);
                }

                ++i;
                if (i % INDEXING_BATCH_SIZE == 0) {
                    QuarkusTransaction.commit();
                    Log.infof("Indexed %d documents...", i);
                    QuarkusTransaction.begin();
                }
            }
        } catch (Throwable t) {
            failure = t;
            throw t;
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
        Log.info("Clearing database...");
        try {
            session.getSessionFactory().getSchemaManager().truncateMappedObjects();
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to clear the database: " + e.getMessage(), e);
        }
    }

}
