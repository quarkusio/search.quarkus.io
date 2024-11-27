package io.quarkus.search.app.indexing;

import static io.quarkus.search.app.util.MutinyUtils.runOnWorkerPool;
import static io.quarkus.search.app.util.MutinyUtils.schedule;
import static io.quarkus.search.app.util.MutinyUtils.waitForeverFor;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.search.app.ReferenceService;
import io.quarkus.search.app.fetching.FetchingService;
import io.quarkus.search.app.hibernate.QuarkusIOLoadingContext;
import io.quarkus.search.app.hibernate.StreamMassIndexingLoggingMonitor;
import io.quarkus.search.app.indexing.reporting.FailureCollector;
import io.quarkus.search.app.indexing.reporting.StatusReporter;
import io.quarkus.search.app.indexing.state.IndexingAlreadyInProgressException;
import io.quarkus.search.app.indexing.state.IndexingState;
import io.quarkus.search.app.quarkusio.QuarkusIO;
import io.quarkus.search.app.util.ExceptionUtils;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.vertx.http.ManagementInterface;

import org.hibernate.search.backend.elasticsearch.ElasticsearchBackend;
import org.hibernate.search.mapper.pojo.standalone.mapping.SearchMapping;
import org.hibernate.search.util.common.impl.Throwables;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;

import io.smallrye.mutiny.subscription.Cancellable;

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

    private IndexingState state;

    @PostConstruct
    void init() {
        state = new IndexingState(StatusReporter.create(indexingConfig.reporting(), Clock.systemUTC()),
                indexingConfig.retry(), this::scheduleIndexing);
    }

    void registerManagementRoutes(@Observes ManagementInterface mi) {
        mi.router().get(REINDEX_ENDPOINT_PATH)
                .blockingHandler(rc -> {
                    reindex(false);
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
                .chain(() -> runOnWorkerPool(() -> {
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
                    reindex(true);
                    return null;
                }))
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
            reindex(true);
            Log.infof("Scheduled reindexing finished.");
        } catch (IndexingAlreadyInProgressException e) {
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
        if (!state.isInProgress()) {
            return;
        }

        var timeout = indexingConfig.timeout();
        var until = Instant.now().plus(timeout);
        do {
            Log.info("Shutdown requested, but indexing is in progress, waiting...");
            Thread.sleep(5000);
        } while (state.isInProgress() && Instant.now().isBefore(until));
        if (state.isInProgress()) {
            throw new IllegalStateException("Shutdown requested, aborting indexing which took more than " + timeout);
        }
    }

    private Cancellable scheduleIndexing(Duration delay) {
        return schedule(delay, () -> runOnWorkerPool(() -> {
            reindex(true);
            return null;
        }))
                .subscribe().with(
                        // We don't care about the items, we just want this to run.
                        ignored -> {
                        },
                        t -> Log.errorf(t, "Reindexing on startup failed: %s", t.getMessage()));
    }

    @ActivateRequestContext
    protected void reindex(boolean allowRetry) {
        try (IndexingState.Attempt attempt = state.tryStart(allowRetry)) {
            try {
                createIndexesIfMissing();
                indexAll(attempt);
            } catch (RuntimeException e) {
                attempt.critical(FailureCollector.Stage.INDEXING, "Indexing failed: " + e.getMessage(), e);
                // Re-throw even though we've reported the failure, for the benefit of callers/logs
                throw e;
            }
        }
    }

    private void createIndexesIfMissing() {
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
                var failFastFailureHandler = new FailFastMassIndexingFailureHandler();
                var future = searchMapping.scope(Object.class).massIndexer()
                        // no point in cleaning the data because of the rollover ^
                        .purgeAllOnStart(false)
                        // data is read-only after indexing -- we may as well have a single segment
                        .mergeSegmentsOnFinish(true)
                        .batchSizeToLoadObjects(indexingConfig.batchSize())
                        .threadsToLoadObjects(indexingConfig.parallelism().orElse(6))
                        .context(QuarkusIOLoadingContext.class, QuarkusIOLoadingContext.of(quarkusIO))
                        .monitor(new StreamMassIndexingLoggingMonitor())
                        .failureHandler(failFastFailureHandler)
                        .start()
                        .toCompletableFuture();
                failFastFailureHandler.init(future);
                try {
                    future.get(indexingConfig.timeout().toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (ExecutionException e) {
                    throw Throwables.toRuntimeException(e.getCause());
                }
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
