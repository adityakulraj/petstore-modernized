package com.mongodb.modernization.petstore.observability;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.modernization.petstore.config.DatabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

@Component
public class DatabaseExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseExecutor.class);
    private final DatabaseProperties properties;
    private final DatabaseTelemetry telemetry;

    /** Creates a database executor and wires its required collaborators. */
    public DatabaseExecutor(DatabaseProperties properties, DatabaseTelemetry telemetry) {
        this.properties = properties;
        this.telemetry = telemetry;
    }

    /** Executes an operation with telemetry and bounded jittered retries when replay is explicitly safe. */
    public <T> T execute(String operation, boolean retrySafe, Supplier<T> action) {
        long started = System.nanoTime();
        int retries = 0;
        try {
            while (true) {
                try {
                    var result = action.get();
                    telemetry.record(operation, System.nanoTime() - started, true, retries);
                    return result;
                } catch (RuntimeException failure) {
                    if (!retrySafe || !isRetryable(failure) || retries + 1 >= properties.retry().maxAttempts()) {
                        telemetry.record(operation, System.nanoTime() - started, false, retries);
                        throw failure;
                    }
                    retries++;
                    long delayMillis = jitteredDelayMillis(retries, properties.retry().initialDelay(),
                            properties.retry().maxDelay());
                    LOG.atWarn()
                            .addKeyValue("event", "database.operation.retry")
                            .addKeyValue("operation", operation)
                            .addKeyValue("retry", retries)
                            .addKeyValue("delayMs", delayMillis)
                            .addKeyValue("exception", failure.getClass().getSimpleName())
                            .log("Retrying transient database operation with exponential jitter");
                    LockSupport.parkNanos(Duration.ofMillis(delayMillis).toNanos());
                    if (Thread.currentThread().isInterrupted()) {
                        telemetry.record(operation, System.nanoTime() - started, false, retries);
                        throw new IllegalStateException("Database retry interrupted", failure);
                    }
                }
            }
        } catch (Error failure) {
            telemetry.record(operation, System.nanoTime() - started, false, retries);
            throw failure;
        }
    }

    /** Executes a void database operation through the same telemetry and retry policy. */
    public void execute(String operation, boolean retrySafe, Runnable action) {
        execute(operation, retrySafe, () -> { action.run(); return null; });
    }

    /** Classifies nested MongoDB, JDBC, and Spring failures without retrying deterministic conflicts. */
    static boolean isRetryable(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            // Inspect every nested cause before treating Spring's outer data-integrity wrapper as deterministic.
            // MongoDB write conflicts are translated to DataIntegrityViolationException but retain an explicit
            // TransientTransactionError label on the nested MongoException.
            if (cause instanceof MongoException mongo &&
                    (mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                            || mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
                            || mongo.hasErrorLabel("RetryableWriteError"))) {
                return true;
            }
            if (cause instanceof MongoSocketException || cause instanceof MongoTimeoutException
                    || cause instanceof SQLTransientException || cause instanceof SQLRecoverableException) {
                return true;
            }
            // Optimistic conflicts and plain constraint failures are deterministic business outcomes, not outages.
            if (cause instanceof OptimisticLockingFailureException || cause instanceof DataIntegrityViolationException) {
                continue;
            }
            if (cause instanceof TransientDataAccessException) return true;
        }
        return false;
    }

    /** Calculates capped exponential equal jitter with a non-zero contention backoff floor. */
    static long jitteredDelayMillis(int retry, Duration initial, Duration maximum) {
        long exponent = 1L << Math.min(retry - 1, 20);
        long cap = Math.min(maximum.toMillis(), Math.multiplyExact(initial.toMillis(), exponent));
        // Equal jitter retains randomness while keeping a non-zero backoff floor. That floor matters when two
        // transactions contend: an immediate sequence of zero-delay retries can exhaust the budget before the
        // winning transaction has committed and made an idempotency record visible.
        if (cap <= 1) return cap;
        long floor = cap / 2;
        return ThreadLocalRandom.current().nextLong(floor, cap + 1);
    }
}
