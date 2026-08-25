package com.mongodb.modernization.petstore.observability;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RequestTelemetry {
    private static final int WINDOW_MINUTES = 60;
    private final Clock clock;
    private final AtomicReferenceArray<Bucket> buckets = new AtomicReferenceArray<>(WINDOW_MINUTES);
    private final LongAdder lifetimeRequests = new LongAdder();
    private final LongAdder lifetimeClientErrors = new LongAdder();
    private final LongAdder lifetimeServerErrors = new LongAdder();

    public RequestTelemetry() { this(Clock.systemUTC()); }

    RequestTelemetry(Clock clock) { this.clock = clock; }

    public void record(String path, int status, long durationNanos) {
        // Dashboard polling and platform probes must not manufacture their own traffic or error rate.
        if (path.startsWith("/api/v1/admin/health") || path.startsWith("/actuator/")) return;
        var epochMinute = clock.instant().getEpochSecond() / 60;
        var bucket = bucketFor(epochMinute);
        bucket.record(status, durationNanos);
        lifetimeRequests.increment();
        if (status >= 500) lifetimeServerErrors.increment();
        else if (status >= 400) lifetimeClientErrors.increment();
    }

    public Snapshot snapshot() {
        var nowMinute = clock.instant().getEpochSecond() / 60;
        var points = new ArrayList<MinutePoint>(WINDOW_MINUTES);
        long windowRequests = 0;
        long windowClientErrors = 0;
        long windowServerErrors = 0;
        long windowTotalNanos = 0;
        long windowMaxNanos = 0;
        for (long minute = nowMinute - WINDOW_MINUTES + 1; minute <= nowMinute; minute++) {
            var bucket = buckets.get(Math.floorMod(minute, WINDOW_MINUTES));
            var point = bucket != null && bucket.epochMinute == minute
                    ? bucket.snapshot()
                    : new MinutePoint(Instant.ofEpochSecond(minute * 60), 0, 0, 0, 0, 0);
            points.add(point);
            windowRequests += point.requests();
            windowClientErrors += point.clientErrors();
            windowServerErrors += point.serverErrors();
            windowTotalNanos += point.totalNanos();
            windowMaxNanos = Math.max(windowMaxNanos, point.maxNanos());
        }
        return new Snapshot(lifetimeRequests.sum(), lifetimeClientErrors.sum(), lifetimeServerErrors.sum(),
                windowRequests, windowClientErrors, windowServerErrors,
                milliseconds(windowTotalNanos, windowRequests), windowMaxNanos / 1_000_000.0,
                List.copyOf(points));
    }

    private Bucket bucketFor(long epochMinute) {
        int index = Math.floorMod(epochMinute, WINDOW_MINUTES);
        while (true) {
            var current = buckets.get(index);
            if (current != null && current.epochMinute == epochMinute) return current;
            var replacement = new Bucket(epochMinute);
            if (buckets.compareAndSet(index, current, replacement)) return replacement;
        }
    }

    private static double milliseconds(long totalNanos, long count) {
        return count == 0 ? 0 : totalNanos / 1_000_000.0 / count;
    }

    private static final class Bucket {
        private final long epochMinute;
        private final LongAdder requests = new LongAdder();
        private final LongAdder clientErrors = new LongAdder();
        private final LongAdder serverErrors = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();

        private Bucket(long epochMinute) { this.epochMinute = epochMinute; }

        private void record(int status, long durationNanos) {
            requests.increment();
            if (status >= 500) serverErrors.increment();
            else if (status >= 400) clientErrors.increment();
            totalNanos.add(durationNanos);
            maxNanos.accumulateAndGet(durationNanos, Math::max);
        }

        private MinutePoint snapshot() {
            return new MinutePoint(Instant.ofEpochSecond(epochMinute * 60), requests.sum(), clientErrors.sum(),
                    serverErrors.sum(), totalNanos.sum(), maxNanos.get());
        }
    }

    public record Snapshot(long lifetimeRequests, long lifetimeClientErrors, long lifetimeServerErrors,
                           long windowRequests, long windowClientErrors, long windowServerErrors,
                           double averageLatencyMs, double maxLatencyMs, List<MinutePoint> series) {
        public double serverErrorRatePercent() {
            return windowRequests == 0 ? 0 : windowServerErrors * 100.0 / windowRequests;
        }

        public double clientErrorRatePercent() {
            return windowRequests == 0 ? 0 : windowClientErrors * 100.0 / windowRequests;
        }
    }

    public record MinutePoint(Instant timestamp, long requests, long clientErrors, long serverErrors,
                              long totalNanos, long maxNanos) {
        public double averageLatencyMs() {
            return requests == 0 ? 0 : totalNanos / 1_000_000.0 / requests;
        }
    }
}
