package com.mongodb.modernization.petstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PetStoreApplication {
    /** Bootstraps the Spring Boot PetStore application. */
    public static void main(String[] args) {
        SpringApplication.run(PetStoreApplication.class, args);
    }
}
