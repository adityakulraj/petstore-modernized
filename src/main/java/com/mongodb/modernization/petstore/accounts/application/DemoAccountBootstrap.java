package com.mongodb.modernization.petstore.accounts.application;

import com.mongodb.modernization.petstore.config.AppProperties;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DemoAccountBootstrap {
    @Bean
    ApplicationRunner seedDemoAccounts(CustomerAccountStore store, CustomerAccountService accounts, AppProperties properties) {
        return ignored -> {
            var demo = properties.demo();
            seedIfMissing(store, accounts, demo.username(), demo.password(), "Alice PetStore", "alice@example.test");
            seedIfMissing(store, accounts, demo.additionalUsername(), demo.additionalPassword(), "Aditya PetStore", "aditya@example.test");
        };
    }

    private static void seedIfMissing(CustomerAccountStore store, CustomerAccountService accounts, String username, String password,
                                      String fullName, String email) {
        if (store.account(username).isEmpty()) {
            accounts.register(username, password, fullName, email, "+1-555-0100",
                    new Address(fullName, "100 Modernization Way", "", "San Francisco", "CA", "94105", "US"),
                    "en", "DOGS", true, true);
        }
    }
}
