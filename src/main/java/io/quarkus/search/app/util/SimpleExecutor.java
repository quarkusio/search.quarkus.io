package io.quarkus.search.app.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.hibernate.search.engine.environment.thread.impl.ThreadPoolProviderImpl;

public class SimpleExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final BlockingQueue<CompletableFuture<?>> submittedTasks = new LinkedBlockingQueue<>();

    public SimpleExecutor(OptionalInt parallelism) {
        int defaultedParallelism = parallelism.orElse(Runtime.getRuntime().availableProcessors());
        executor = new ThreadPoolExecutor(
                defaultedParallelism,
                defaultedParallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(defaultedParallelism * 2),
                new ThreadPoolProviderImpl.BlockPolicy());
    }

    @Override
    public void close() {
        List<?> remainingTasks = executor.shutdownNow();
        if (!remainingTasks.isEmpty()) {
            throw new IllegalStateException("Executor closed with %d remaining tasks".formatted(remainingTasks.size()));
        }
    }

    public CompletableFuture<Void> submit(Runnable runnable) {
        return record(CompletableFuture.runAsync(runnable, executor));
    }

    public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        return record(CompletableFuture.supplyAsync(supplier, executor));
    }

    private <T> CompletableFuture<T> record(CompletableFuture<T> future) {
        submittedTasks.add(future);
        return future;
    }

    public void waitForSuccessOrThrow(Duration timeout) {
        List<CompletableFuture<?>> submittedSoFar = new ArrayList<>();
        submittedTasks.drainTo(submittedSoFar);
        try {
            CompletableFuture.allOf(submittedSoFar.toArray(CompletableFuture<?>[]::new))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw ExceptionUtils.toRuntimeException(e.getCause());
        }
    }
}
