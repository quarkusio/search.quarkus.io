package io.quarkus.search.app.indexing;

import static io.quarkus.search.app.util.MutinyUtils.waitForeverFor;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.search.app.ReferenceService;
import io.quarkus.search.app.fetching.FetchingService;
import io.quarkus.search.app.hibernate.QuarkusIOLoadingContext;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.util.ExceptionUtils;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.http.ManagementInterface;

import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;

@ApplicationScoped
public class IndexingService {

    private static final String REINDEX_ENDPOINT_PATH = "/reindex";

    @Inject
    SearchMapping searchMapping;

    @Inject
    FetchingService fetchingService;

    @Inject
    IndexingConfig indexingConfig;

    @Inject
    ReferenceService referenceService;

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
            case INDEXES_EMPTY -> Log.infof("Reindexing on startup if indexes are empty");
            case NEVER -> {
                Log.infof("Not reindexing on startup: disabled through configuration."
                        + " Call endpoint '%s' to reindex explicitly.",
                        REINDEX_ENDPOINT_PATH);
                return;
            }
        }
        var waitInterval = indexingConfig.onStartup().waitInterval();
        waitForeverFor(this::isSearchBackendReachable, waitInterval,
                () -> Log.infof("Reindexing on startup: search backend is not reachable yet, waiting..."))
                .chain(() -> waitForeverFor(this::isSearchBackendReady, waitInterval,
                        () -> Log.infof("Reindexing on startup: search backend is not ready yet, waiting...")))
                .chain(() -> Uni.createFrom()
                        .item(() -> {
                            if (IndexingConfig.OnStartup.When.INDEXES_EMPTY.equals(indexingConfig.onStartup().when())) {
                                try (var session = searchMapping.createSession()) {
                                    long documentCount = session.search(Object.class)
                                            .where(f -> f.matchAll())
                                            .fetchTotalHitCount();
                                    if (documentCount > 0L) {
                                        Log.infof("Not reindexing on startup:"
                                                + " index are present, reachable, and contain %s documents."
                                                + " Call endpoint '%s' to reindex explicitly.",
                                                documentCount, REINDEX_ENDPOINT_PATH);
                                        return null;
                                    }
                                    Log.infof("Reindexing on startup: indexes are empty.");
                                } catch (RuntimeException e) {
                                    Log.infof(
                                            e, "Reindexing on startup: could not determine the content of indexes");
                                }
                            }
                            reindex();
                            return null;
                        })
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
                .subscribe().with(
                        // We don't care about the items, we just want this to run.
                        ignored -> {
                        },
                        t -> Log.errorf(t, "Reindexing on startup failed: %s", t.getMessage()));
    }

    @Scheduled(cron = "{indexing.scheduled.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void indexOnTime() {
        try {
            Log.infof("Scheduled reindexing starting...");
            reindex();
            Log.infof("Scheduled reindexing finished.");
        } catch (ReindexingAlreadyInProgressException e) {
            Log.infof("Indexing was already started by some other process.");
        } catch (RuntimeException e) {
            Log.errorf(e, "Failed to start scheduled reindexing: %s", e.getMessage());
        }
    }

    private boolean isSearchBackendReachable() {
        try {
            searchMapping.backend().unwrap(ElasticsearchBackend.class).client(RestClient.class)
                    .performRequest(new Request("GET", "/"));
            return true;
        } catch (IOException e) {
            Log.debug("Caught exception when testing whether the search backend is reachable", e);
            return false;
        }
    }

    private boolean isSearchBackendReady() {
        try {
            searchMapping.backend().unwrap(ElasticsearchBackend.class).client(RestClient.class)
                    .performRequest(new Request("GET", "/_cluster/health?wait_for_status=green&timeout=0s"));
            return true;
        } catch (IOException e) {
            Log.debug("Caught exception when testing whether the search backend is reachable", e);
            return false;
        }
    }

    @SuppressWarnings("BusyWait")
    @PreDestroy
    protected void waitForReindexingToFinish() throws InterruptedException {
        if (!reindexingInProgress.get()) {
            return;
        }

        var timeout = indexingConfig.timeout();
        var until = Instant.now().plus(timeout);
        do {
            Log.info("Shutdown requested, but indexing is in progress, waiting...");
            Thread.sleep(5000);
        } while (reindexingInProgress.get() && Instant.now().isBefore(until));
        if (reindexingInProgress.get()) {
            throw new IllegalStateException("Shutdown requested, aborting indexing which took more than " + timeout);
        }
    }

    @ActivateRequestContext
    protected void reindex() {
        // Reindexing requires exclusive access to the DB/indexes
        if (!reindexingInProgress.compareAndSet(false, true)) {
            throw new ReindexingAlreadyInProgressException();
        }
        try (FailureCollector failureCollector = new FailureCollector(indexingConfig.errorReporting())) {
            try {
                createIndexes();
                indexAll(failureCollector);
            } catch (RuntimeException e) {
                failureCollector.critical(FailureCollector.Stage.INDEXING, "Indexing failed: " + e.getMessage(), e);
                // Re-throw even though we've reported the failure, for the benefit of callers/logs
                throw e;
            }
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
            Log.error("Creating missing indexes failed; will attempt to recover mismatched aliases... ", e);
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
                    return;
                }
            } catch (RuntimeException e2) {
                e.addSuppressed(e2);
            }
            throw new IllegalStateException("Failed to create indexes: " + e.getMessage(), e);
        }
    }

    private void indexAll(FailureCollector failureCollector) {
        Log.info("Indexing...");
        try (Rollover rollover = Rollover.start(searchMapping)) {
            try (QuarkusIO quarkusIO = fetchingService.fetchQuarkusIo(failureCollector)) {
                Log.info("Indexing quarkus.io...");
                searchMapping.scope(Object.class).massIndexer()
                        // no point in cleaning the data because of the rollover ^
                        .purgeAllOnStart(false)
                        .batchSizeToLoadObjects(indexingConfig.batchSize())
                        .threadsToLoadObjects(indexingConfig.parallelism().orElse(6))
                        .context(QuarkusIOLoadingContext.class, QuarkusIOLoadingContext.of(quarkusIO))
                        // this could lead to a BlockedThreadChecker complaining if called through /reindex
                        //  [i.ve.co.im.BlockedThreadChecker] Thread Thread[vert.x-worker-thread-1,5,main] has been blocked for 61076 ms, time limit is 60000 ms:
                        .startAndWait();
            }

            rollover.commit();
            referenceService.invalidateCaches();
            Log.info("Indexing success");
        } catch (RuntimeException | IOException e) {
            throw new IllegalStateException("Failed to index data: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ExceptionUtils.toRuntimeException(e);
        }
    }
}
