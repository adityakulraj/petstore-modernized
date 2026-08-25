package com.mongodb.modernization.petstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.database")
public record DatabaseProperties(Pool pool, Retry retry) {
    public record Pool(int minSize, int maxSize, Duration maxWait, Duration maxIdle) {}

    public record Retry(int maxAttempts, Duration initialDelay, Duration maxDelay) {}
}
