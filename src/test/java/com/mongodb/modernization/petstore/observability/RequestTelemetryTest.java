package com.mongodb.modernization.petstore.observability;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTelemetryTest {
    @Test
    void classifiesErrorsAndCalculatesRollingLatencyWithoutCountingHealthPolls() {
        var clock = new MutableClock(Instant.parse("2026-08-26T10:15:30Z"));
        var telemetry = new RequestTelemetry(clock);

        telemetry.record("/api/v1/catalog/products", 200, Duration.ofMillis(10).toNanos());
        telemetry.record("/api/v1/catalog/missing", 404, Duration.ofMillis(20).toNanos());
        telemetry.record("/error", 503, Duration.ofMillis(30).toNanos());
        telemetry.record("/api/v1/admin/health", 200, Duration.ofSeconds(5).toNanos());
        telemetry.record("/actuator/health/readiness", 503, Duration.ofSeconds(5).toNanos());

        var snapshot = telemetry.snapshot();
        assertThat(snapshot.lifetimeRequests()).isEqualTo(3);
        assertThat(snapshot.windowClientErrors()).isEqualTo(1);
        assertThat(snapshot.windowServerErrors()).isEqualTo(1);
        assertThat(snapshot.clientErrorRatePercent()).isEqualTo(100.0 / 3);
        assertThat(snapshot.serverErrorRatePercent()).isEqualTo(100.0 / 3);
        assertThat(snapshot.averageLatencyMs()).isEqualTo(20);
        assertThat(snapshot.maxLatencyMs()).isEqualTo(30);
        assertThat(snapshot.series()).hasSize(60);
        assertThat(snapshot.series().getLast().requests()).isEqualTo(3);
    }

    @Test
    void expiresMinuteBucketsAfterTheSixtyMinuteWindow() {
        var clock = new MutableClock(Instant.parse("2026-08-26T10:00:00Z"));
        var telemetry = new RequestTelemetry(clock);
        telemetry.record("/first", 200, 1);

        clock.advance(Duration.ofMinutes(59));
        telemetry.record("/last", 200, 1);
        assertThat(telemetry.snapshot().windowRequests()).isEqualTo(2);

        clock.advance(Duration.ofMinutes(1));
        assertThat(telemetry.snapshot().windowRequests()).isEqualTo(1);
        assertThat(telemetry.snapshot().lifetimeRequests()).isEqualTo(2);
    }

    @Test
    void recordsConcurrentRequestsWithoutLosingUpdates() throws Exception {
        var telemetry = new RequestTelemetry(new MutableClock(Instant.parse("2026-08-26T10:00:00Z")));
        int workers = 8;
        int requestsPerWorker = 2_000;
        try (var executor = Executors.newFixedThreadPool(workers)) {
            for (int worker = 0; worker < workers; worker++) {
                executor.submit(() -> {
                    for (int request = 0; request < requestsPerWorker; request++) {
                        telemetry.record("/concurrent", 200, 1_000_000);
                    }
                });
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(telemetry.snapshot().windowRequests()).isEqualTo((long) workers * requestsPerWorker);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private MutableClock(Instant instant) { this.instant = new AtomicReference<>(instant); }
        private void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
    }
}
