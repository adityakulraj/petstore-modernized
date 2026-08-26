package com.mongodb.modernization.petstore.config;

import com.mongodb.modernization.petstore.accounts.application.CustomerAccountStore;
import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void configuresCustomersAndAdminWithEncodedPasswordsAndSeparateRoles() {
        var config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        var properties = new AppProperties(AppProperties.Store.MONGO,
                new AppProperties.Demo("alice", "petstore-demo", "aditya", "password"),
                new AppProperties.Admin("admin", "admin", new BigDecimal("500.00")),
                new AppProperties.Supplier("supplier", "supplier"));

        var address = new Address("Alice", "1 Main St", "", "Pune", "MH", "411001", "IN");
        CustomerAccountStore accounts = new CustomerAccountStore() {
            @Override public java.util.Optional<CustomerAccount> account(String username) {
                return switch (username) {
                    case "alice" -> java.util.Optional.of(new CustomerAccount("alice", encoder.encode("petstore-demo"), "Alice", "alice@example.test", "1", address, "en", "DOGS", true, true));
                    case "aditya" -> java.util.Optional.of(new CustomerAccount("aditya", encoder.encode("password"), "Aditya", "aditya@example.test", "1", address, "en", "CATS", true, true));
                    default -> java.util.Optional.empty();
                };
            }
            @Override public CustomerAccount save(CustomerAccount account) { return account; }
        };

        UserDetailsService users = config.users(properties, accounts, encoder);

        var alice = users.loadUserByUsername("alice");
        var aditya = users.loadUserByUsername("aditya");
        var admin = users.loadUserByUsername("admin");
        var supplier = users.loadUserByUsername("supplier");
        assertThat(encoder.matches("petstore-demo", alice.getPassword())).isTrue();
        assertThat(encoder.matches("password", aditya.getPassword())).isTrue();
        assertThat(encoder.matches("admin", admin.getPassword())).isTrue();
        assertThat(aditya.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
        assertThat(admin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(encoder.matches("supplier", supplier.getPassword())).isTrue();
        assertThat(supplier.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_SUPPLIER");

        var manager = new ProviderManager(config.authenticationProvider(users, encoder));
        var authentication = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", "admin"));
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).contains("ROLE_ADMIN");
        assertThat(manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", "admin")).isAuthenticated()).isTrue();
        assertThat(manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("supplier", "supplier")).isAuthenticated()).isTrue();
        assertThat(manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("supplier", "supplier")).isAuthenticated()).isTrue();
    }
}
