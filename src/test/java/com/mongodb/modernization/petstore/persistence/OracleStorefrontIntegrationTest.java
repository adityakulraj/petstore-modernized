package com.mongodb.modernization.petstore.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

@SpringBootTest(properties = {"spring.profiles.active=oracle", "app.store=oracle", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Testcontainers(disabledWithoutDocker = true)
class OracleStorefrontIntegrationTest extends StorefrontStoreContract {
    @Container
    // GHCR is reachable in environments where Docker Hub is intercepted, and publishes native ARM64 images.
    static final OracleContainer ORACLE = new OracleContainer(
            DockerImageName.parse("ghcr.io/gvenzl/oracle-free:23.26.2-slim")
                    .asCompatibleSubstituteFor("gvenzl/oracle-free"))
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
    }
}
