package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.config.DatabaseProperties;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

@Configuration
@Profile("mongo")
class MongoObservabilityConfig {
    /** Configures or applies mongo pool metrics behavior for the application runtime. */
    @Bean MongoPoolMetrics mongoPoolMetrics() { return new MongoPoolMetrics(); }

    @Bean
    /** Configures or applies monitored mongo pool behavior for the application runtime. */
    MongoClientSettingsBuilderCustomizer monitoredMongoPool(DatabaseProperties properties, MongoPoolMetrics metrics) {
        return settings -> {
            settings.retryReads(true).retryWrites(true);
            settings.applyToConnectionPoolSettings(pool -> pool
                    .minSize(properties.pool().minSize())
                    .maxSize(properties.pool().maxSize())
                    .maxWaitTime(properties.pool().maxWait().toMillis(), TimeUnit.MILLISECONDS)
                    .maxConnectionIdleTime(properties.pool().maxIdle().toMillis(), TimeUnit.MILLISECONDS)
                    .addConnectionPoolListener(metrics));
        };
    }
}
