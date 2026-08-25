package com.mongodb.modernization.petstore.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void configuresBothDemoCustomersWithEncodedPasswords() {
        var config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        var properties = new AppProperties(AppProperties.Store.MONGO,
                new AppProperties.Demo("alice", "petstore-demo", "aditya", "password"));

        UserDetailsService users = config.users(properties, encoder);

        var alice = users.loadUserByUsername("alice");
        var aditya = users.loadUserByUsername("aditya");
        assertThat(encoder.matches("petstore-demo", alice.getPassword())).isTrue();
        assertThat(encoder.matches("password", aditya.getPassword())).isTrue();
        assertThat(aditya.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
    }
}
