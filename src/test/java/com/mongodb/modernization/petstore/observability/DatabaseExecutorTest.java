package com.mongodb.modernization.petstore.observability;

import com.mongodb.MongoException;
import com.mongodb.modernization.petstore.config.DatabaseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseExecutorTest {
    @Test
    void retriesTransientSafeOperationsAndPublishesRetryTelemetry() {
        var telemetry = new DatabaseTelemetry();
        var executor = executor(telemetry);
        var attempts = new AtomicInteger();

        var result = executor.execute("orders.checkout", true, () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                throw new RuntimeException(new SQLTransientConnectionException("temporary"));
            }
            if (attempt == 2) {
                var writeConflict = new MongoException(112, "write conflict");
                writeConflict.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
                throw new DataIntegrityViolationException("translated by Spring", writeConflict);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        assertThat(telemetry.snapshot()).singleElement().satisfies(operation -> {
            assertThat(operation.operation()).isEqualTo("orders.checkout");
            assertThat(operation.calls()).isEqualTo(1);
            assertThat(operation.failures()).isZero();
            assertThat(operation.retries()).isEqualTo(2);
        });
    }

    @Test
    void neverRetriesUnsafeOrDeterministicConstraintFailures() {
        var telemetry = new DatabaseTelemetry();
        var executor = executor(telemetry);
        var unsafeAttempts = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute("cart.add", false, () -> {
            unsafeAttempts.incrementAndGet();
            throw new RuntimeException(new SQLTransientConnectionException("ambiguous write"));
        })).isInstanceOf(RuntimeException.class);
        assertThat(unsafeAttempts).hasValue(1);

        var duplicateAttempts = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute("orders.checkout", true, () -> {
            duplicateAttempts.incrementAndGet();
            throw new DuplicateKeyException("deterministic");
        })).isInstanceOf(DuplicateKeyException.class);
        assertThat(duplicateAttempts).hasValue(1);
        assertThat(telemetry.snapshot()).allMatch(operation -> operation.failures() == 1);
    }

    @Test
    void exponentialEqualJitterStaysBetweenItsFloorAndCappedDelay() {
        for (int retry = 1; retry <= 8; retry++) {
            long delay = DatabaseExecutor.jitteredDelayMillis(retry, Duration.ofMillis(25), Duration.ofMillis(200));
            long cap = Math.min(200, 25L << (retry - 1));
            assertThat(delay).isBetween(cap / 2, cap);
        }
    }

    private static DatabaseExecutor executor(DatabaseTelemetry telemetry) {
        var properties = new DatabaseProperties(
                new DatabaseProperties.Pool(1, 10, Duration.ofSeconds(1), Duration.ofMinutes(1)),
                new DatabaseProperties.Retry(3, Duration.ZERO, Duration.ZERO));
        return new DatabaseExecutor(properties, telemetry);
    }
}
