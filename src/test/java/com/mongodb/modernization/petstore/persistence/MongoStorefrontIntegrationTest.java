package com.mongodb.modernization.petstore.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

@SpringBootTest(properties = {"spring.profiles.active=mongo", "app.store=mongo"})
@Testcontainers(disabledWithoutDocker = true)
class MongoStorefrontIntegrationTest extends StorefrontStoreContract {
    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.2.11").withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("petstore"));
    }
}
