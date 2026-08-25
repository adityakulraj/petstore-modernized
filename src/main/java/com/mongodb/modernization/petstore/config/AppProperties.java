package com.mongodb.modernization.petstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(Store store, Demo demo, Admin admin) {
    public enum Store { ORACLE, MONGO }

    public record Demo(String username, String password,
                       String additionalUsername, String additionalPassword) {}

    public record Admin(String username, String password) {}
}
