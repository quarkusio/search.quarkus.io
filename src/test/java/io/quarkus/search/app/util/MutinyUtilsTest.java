package io.quarkus.search.app.util;

import static io.quarkus.search.app.util.MutinyUtils.runOnWorkerPool;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MutinyUtilsTest {
    @Test
    void schedule() {
        Supplier<Void> action = mock();
        Consumer<Void> itemCallback = mock();
        Consumer<Throwable> failureCallback = mock();

        MutinyUtils.schedule(Duration.ofSeconds(2), () -> runOnWorkerPool(action))
                .subscribe().with(itemCallback, failureCallback);

        // Delayed execution should happen after approximately two seconds
        // (and definitely no earlier than after one second).
        await().between(Duration.ofSeconds(1), Duration.ofSeconds(4))
                .untilAsserted(() -> verify(action).get());

        verify(itemCallback).accept(null);
        verifyNoInteractions(failureCallback);
    }

    @Test
    void schedule_cancel() {
        Supplier<Void> action = mock();
        Consumer<Void> itemCallback = mock();
        Consumer<Throwable> failureCallback = mock();

        var cancellable = MutinyUtils.schedule(Duration.ofSeconds(2), () -> runOnWorkerPool(action))
                .subscribe().with(itemCallback, failureCallback);

        // Cancel immediately
        cancellable.cancel();

        // Cancellation should prevent the delayed execution.
        // We'll consider that if it doesn't happen after 4 seconds, it'll likely never happen (as expected).
        await().during(Duration.ofSeconds(4))
                .untilAsserted(() -> verifyNoInteractions(action));
        verifyNoInteractions(itemCallback, failureCallback);
    }
}
