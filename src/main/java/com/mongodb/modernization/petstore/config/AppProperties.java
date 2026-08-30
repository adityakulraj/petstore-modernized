package com.mongodb.modernization.petstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("app")
/** Creates a app properties and wires its required collaborators. */
public record AppProperties(Store store, Demo demo, Admin admin, Supplier supplier) {
    public enum Store { ORACLE, MONGO }

    /** Configures or applies demo behavior for the application runtime. */
    public record Demo(String username, String password,
                       String additionalUsername, String additionalPassword) {}

    /** Configures or applies admin behavior for the application runtime. */
    public record Admin(String username, String password, BigDecimal approvalThreshold) {}

    /** Configures or applies supplier behavior for the application runtime. */
    public record Supplier(String username, String password) {}
}
