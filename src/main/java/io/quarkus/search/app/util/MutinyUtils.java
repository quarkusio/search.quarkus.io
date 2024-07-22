package io.quarkus.search.app.util;

import java.time.Duration;
import java.util.function.Supplier;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.FixedDemandPacer;

public final class MutinyUtils {
    private MutinyUtils() {
    }

    public static Uni<Void> waitForeverFor(Supplier<Boolean> condition, Duration step, Runnable onRetry) {
        // https://smallrye.io/smallrye-mutiny/2.0.0/guides/polling/#how-to-use-polling
        return Multi.createBy().repeating()
                .supplier(condition)
                .until(conditionMet -> conditionMet)
                .onItem().invoke(onRetry)
                // https://smallrye.io/smallrye-mutiny/2.5.1/guides/controlling-demand/#pacing-the-demand
                .paceDemand().on(Infrastructure.getDefaultWorkerPool())
                .using(new FixedDemandPacer(1L, step))
                // https://stackoverflow.com/a/72805321/6692043
                .skip().where(ignored -> true)
                .toUni().replaceWithVoid();
    }

    public static <T> Uni<T> runOnWorkerPool(Supplier<T> action) {
        return Uni.createFrom()
                .item(action)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    public static <T> Uni<T> schedule(Duration delay, Supplier<Uni<T>> action) {
        return Uni.createFrom().nullItem()
                .onItem().delayIt().by(delay)
                .onItem().transformToUni(ignored -> action.get());
    }
}
