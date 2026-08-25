package com.mongodb.modernization.petstore.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import java.time.Duration;

@SpringBootTest(properties = {"spring.profiles.active=oracle", "app.store=oracle", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Testcontainers(disabledWithoutDocker = true)
class OracleStorefrontIntegrationTest extends StorefrontStoreContract {
    @Container
    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23.26.2-slim-faststart")
            .withStartupTimeout(Duration.ofMinutes(5));

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", ORACLE::getUsername);
        registry.add("spring.datasource.password", ORACLE::getPassword);
    }
}
