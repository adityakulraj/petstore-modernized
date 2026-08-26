package com.mongodb.modernization.petstore.catalog.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {
    @Test
    void itemVariantsHaveCustomerFacingNamesWhileStandardItemsKeepTheProductName() {
        var bulldog = new Product("K9-BD-01", "K9-BD", "Male Adult", "DOGS", "Dogs", "Bulldog",
                "Companion", new BigDecimal("850.00"), 4, 0);
        var canary = new Product("AV-CB-01", "BIRDS", "Birds", "Canary", "Songbird",
                new BigDecimal("125.00"), 10, 0);

        assertThat(bulldog.displayName()).isEqualTo("Male Adult Bulldog");
        assertThat(canary.displayName()).isEqualTo("Canary");
    }
}
