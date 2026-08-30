package com.mongodb.modernization.petstore.observability;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class DatabaseTelemetry {
    private final ConcurrentHashMap<String, OperationCounters> operations = new ConcurrentHashMap<>();

    /** Collects or updates observability data for record. */
    void record(String operation, long durationNanos, boolean success, int retries) {
        operations.computeIfAbsent(operation, ignored -> new OperationCounters())
                .record(durationNanos, success, retries);
    }

    /** Collects or updates observability data for snapshot. */
    public List<OperationSnapshot> snapshot() {
        return operations.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparing(OperationSnapshot::operation))
                .toList();
    }

    private static final class OperationCounters {
        private final LongAdder calls = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder retries = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        /** Collects or updates observability data for record. */
        private void record(long durationNanos, boolean success, int retryCount) {
            calls.increment();
            if (!success) failures.increment();
            retries.add(retryCount);
            totalNanos.add(durationNanos);
            maxNanos.accumulateAndGet(durationNanos, Math::max);
        }

        /** Collects or updates observability data for snapshot. */
        private OperationSnapshot snapshot(String operation) {
            long count = calls.sum();
            return new OperationSnapshot(operation, count, failures.sum(), retries.sum(),
                    count == 0 ? 0 : totalNanos.sum() / 1_000_000.0 / count,
                    maxNanos.get() / 1_000_000.0);
        }
    }

    /** Collects or updates observability data for operation snapshot. */
    public record OperationSnapshot(String operation, long calls, long failures, long retries,
                                    double averageLatencyMs, double maxLatencyMs) {}
}
