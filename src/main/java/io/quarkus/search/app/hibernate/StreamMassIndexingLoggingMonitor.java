package io.quarkus.search.app.hibernate;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;

import io.quarkus.logging.Log;

import org.hibernate.search.mapper.pojo.massindexing.MassIndexingMonitor;

/**
 * This monitor can be used for mass indexing a stream data where we do not know the total number of documents
 * prior to the finishing of indexing.
 * <p>
 * Essentially a copy of a built-in monitor
 * {@link org.hibernate.search.mapper.pojo.massindexing.impl.PojoMassIndexingLoggingMonitor},
 * with the difference, that this one does not know of a total count and does not calculate % progress.
 */
public class StreamMassIndexingLoggingMonitor implements MassIndexingMonitor {

    private static final int LOG_AFTER_NUMBER_OF_DOCUMENTS = 50;

    private final AtomicLong documentsDoneCounter = new AtomicLong();
    private final AtomicReference<StatusMessageInfo> lastMessageInfo = new AtomicReference<>();
    private volatile long startTime;

    @Override
    public void documentsAdded(long increment) {
        if (startTime == 0) {
            // this sync block doesn't seem to be a problem for Loom:
            // - always executed in MassIndexer threads, which are not virtual threads
            // - no I/O and simple in-memory operations
            synchronized (this) {
                if (startTime == 0) {
                    long theStartTime = System.nanoTime();
                    lastMessageInfo.set(new StatusMessageInfo(startTime, 0));
                    // Do this last, so other threads will block until we're done initializing lastMessageInfo.
                    startTime = theStartTime;
                }
            }
        }

        long previous = documentsDoneCounter.getAndAdd(increment);
        /*
         * Only log if the current increment was the one that made the counter
         * go to a higher multiple of the period.
         */
        long current = previous + increment;
        if ((previous / LOG_AFTER_NUMBER_OF_DOCUMENTS) < (current / LOG_AFTER_NUMBER_OF_DOCUMENTS)) {
            long currentTime = System.nanoTime();
            printStatusMessage(startTime, currentTime, current);
        }
    }

    @Override
    public void documentsBuilt(long number) {
        //not used
    }

    @Override
    public void entitiesLoaded(long size) {
        //not used
    }

    @Override
    public void addToTotalCount(long count) {
        Log.info("Mass indexing is going to index an entity stream. Total is not known at this point.");
    }

    @Override
    public void indexingCompleted() {
        Log.infof("Mass indexing complete. Indexed %1$d entities.", documentsDoneCounter.get());
    }

    protected void printStatusMessage(long startTime, long currentTime, long doneCount) {
        StatusMessageInfo currentStatusMessageInfo = new StatusMessageInfo(
                currentTime, doneCount);
        StatusMessageInfo previousStatusMessageInfo = lastMessageInfo.getAndAccumulate(
                currentStatusMessageInfo,
                StatusMessageInfo.UPDATE_IF_MORE_UP_TO_DATE_FUNCTION);

        // Avoid logging outdated info if logging happened concurrently since we last called System.nanoTime()
        if (!currentStatusMessageInfo.isMoreUpToDateThan(previousStatusMessageInfo)) {
            return;
        }

        long elapsedNano = currentTime - startTime;
        // period between two log events might be too short to use millis as a result infinity speed will be displayed.
        long intervalBetweenLogsNano = currentStatusMessageInfo.currentTime - previousStatusMessageInfo.currentTime;

        Log.infof("Mass indexing progress: indexed %1$d entities in %2$d ms.", doneCount,
                TimeUnit.NANOSECONDS.toMillis(elapsedNano));
        if (Log.isDebugEnabled()) {
            float estimateSpeed = doneCount * 1_000_000_000f / elapsedNano;
            float currentSpeed = (currentStatusMessageInfo.documentsDone
                    - previousStatusMessageInfo.documentsDone) * 1_000_000_000f / intervalBetweenLogsNano;
            Log.debugf("Mass indexing progress: Statistics: "
                    + "Mass indexing speed: %1$f documents/second since last message, %2$f documents/second since start.",
                    currentSpeed, estimateSpeed);
        }
    }

    private record StatusMessageInfo(long currentTime, long documentsDone) {
        public static final BinaryOperator<StatusMessageInfo> UPDATE_IF_MORE_UP_TO_DATE_FUNCTION = (
                StatusMessageInfo storedVal,
                StatusMessageInfo newVal) -> newVal.isMoreUpToDateThan(storedVal) ? newVal
                        : storedVal;

        public boolean isMoreUpToDateThan(StatusMessageInfo other) {
            return documentsDone > other.documentsDone
                    // Ensure we log status updates even if the mass indexer is stuck for a long time
                    || documentsDone == other.documentsDone && currentTime > other.currentTime;
        }
    }
}
