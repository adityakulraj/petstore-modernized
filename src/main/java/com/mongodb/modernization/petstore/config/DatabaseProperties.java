package com.mongodb.modernization.petstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.database")
/** Creates a database properties and wires its required collaborators. */
public record DatabaseProperties(Pool pool, Retry retry) {
    /** Configures or applies pool behavior for the application runtime. */
    public record Pool(int minSize, int maxSize, Duration maxWait, Duration maxIdle) {}

    /** Configures or applies retry behavior for the application runtime. */
    public record Retry(int maxAttempts, Duration initialDelay, Duration maxDelay) {}
}
