package com.mongodb.modernization.petstore.persistence;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"spring.profiles.active=mongo", "app.store=mongo",
        "app.notifications.poll-interval=1h"})
@Testcontainers(disabledWithoutDocker = true)
class MongoStorefrontIntegrationTest extends StorefrontStoreContract {
    @Container
    // Google's official Docker Hub mirror avoids local proxy/certificate failures without changing the image contents.
    static final MongoDBContainer MONGO = new MongoDBContainer(
            DockerImageName.parse("mirror.gcr.io/library/mongo:8.2.11").asCompatibleSubstituteFor("mongo"))
            .withReplicaSet();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("petstore"));
    }
}
