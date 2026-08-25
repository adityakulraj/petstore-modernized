package com.mongodb.modernization.petstore.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void configuresCustomersAndAdminWithEncodedPasswordsAndSeparateRoles() {
        var config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        var properties = new AppProperties(AppProperties.Store.MONGO,
                new AppProperties.Demo("alice", "petstore-demo", "aditya", "password"),
                new AppProperties.Admin("admin", "admin"));

        UserDetailsService users = config.users(properties, encoder);

        var alice = users.loadUserByUsername("alice");
        var aditya = users.loadUserByUsername("aditya");
        var admin = users.loadUserByUsername("admin");
        assertThat(encoder.matches("petstore-demo", alice.getPassword())).isTrue();
        assertThat(encoder.matches("password", aditya.getPassword())).isTrue();
        assertThat(encoder.matches("admin", admin.getPassword())).isTrue();
        assertThat(aditya.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
        assertThat(admin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }
}
