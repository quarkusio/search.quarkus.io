package io.quarkus.search.app.indexing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.search.app.fetching.FetchingService;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.util.SimpleExecutor;

import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.http.ManagementInterface;

import org.hibernate.Session;
import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.mapper.orm.mapping.SearchMapping;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.util.common.impl.Closer;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.FixedDemandPacer;

@ApplicationScoped
public class IndexingService {

    private static final String REINDEX_ENDPOINT_PATH = "/reindex";

    @Inject
    SearchMapping searchMapping;

    @Inject
    Session session;

    @Inject
    SearchSession searchSession;

    @Inject
    FetchingService fetchingService;

    @Inject
    IndexingConfig indexingConfig;

    private final AtomicBoolean reindexingInProgress = new AtomicBoolean();

    void registerManagementRoutes(@Observes ManagementInterface mi) {
        mi.router().get(REINDEX_ENDPOINT_PATH)
                .blockingHandler(rc -> {
                    reindex();
                    rc.end("Success");
                });
    }

    void indexOnStartup(@Observes StartupEvent ev) {
        switch (indexingConfig.onStartup().when()) {
            case ALWAYS -> Log.infof("Reindexing on startup");
            case INDEXES_EMPTY -> {
                try {
                    long documentCount = QuarkusTransaction.requiringNew().call(() -> searchSession.search(
                            Object.class)
                            .where(f -> f.matchAll())
                            .fetchTotalHitCount());
                    if (documentCount >= 0L) {
                        Log.infof("Not reindexing on startup: index are present, reachable, and contain %s documents."
                                + " Call endpoint '%s' to reindex explicitly.",
                                documentCount, REINDEX_ENDPOINT_PATH);
                        return;
                    }
                    Log.infof("Reindexing on startup: indexes are empty.");
                } catch (RuntimeException e) {
                    Log.infof(e, "Reindexing on startup: could not determine the content of indexes.");
                }
                return;
            }
            case NEVER -> {
                Log.infof("Not reindexing on startup: disabled through configuration."
                        + " Call endpoint '%s' to reindex explicitly.",
                        REINDEX_ENDPOINT_PATH);
                return;
            }
        }
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

    @Scheduled(cron = "{indexing.scheduled.cron}")
    void indexOnTime() {
        try {
            Log.infof("Scheduled reindex starting...");
            reindex();
            Log.infof("Scheduled reindex finished.");
        } catch (ReindexingAlreadyInProgressException e) {
            Log.infof("Indexing was already started by some other process.");
        } catch (Exception e) {
            Log.errorf(e, "Failed to start scheduled reindex: %s", e.getMessage());
        }
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
        try (var guideStream = quarkusIO.guides();
                var executor = new SimpleExecutor(indexingConfig.parallelism())) {
            indexAll(executor, guideStream.iterator());
        }
    }

    private <T> void indexAll(SimpleExecutor executor, Iterator<T> docIterator) {
        LongAdder indexedCount = new LongAdder();
        while (docIterator.hasNext()) {
            List<T> docs = new ArrayList<>();
            for (int i = 0; docIterator.hasNext() && i < indexingConfig.batchSize(); i++) {
                docs.add(docIterator.next());
            }
            executor.submit(() -> {
                indexBatch(docs);
                indexedCount.add(docs.size());
                // This might lead to duplicate logs, but we don't care.
                Log.infof("Indexed %d documents...", indexedCount.longValue());
            });
        }
        executor.waitForSuccessOrThrow(indexingConfig.timeout());
        Log.infof("Indexed %d documents.", indexedCount.longValue());
    }

    @ActivateRequestContext
    <T> void indexBatch(List<T> docs) {
        QuarkusTransaction.requiringNew().run(() -> {
            for (T doc : docs) {
                try {
                    Log.tracef("About to persist: %s", doc);
                    session.persist(doc);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to persist '%s': %s".formatted(doc, e.getMessage()), e);
                }
            }
        });
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
