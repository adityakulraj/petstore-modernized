package com.mongodb.modernization.petstore.accounts.application;

import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerAccountServiceTest {
    private final HashMap<String, CustomerAccount> accounts = new HashMap<>();
    private final CustomerAccountStore store = new CustomerAccountStore() {
        @Override public Optional<CustomerAccount> account(String username) { return Optional.ofNullable(accounts.get(username)); }
        @Override public CustomerAccount save(CustomerAccount account) { accounts.put(account.username(), account); return account; }
    };
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final CustomerAccountService service = new CustomerAccountService(store, passwords);
    private final Address address = new Address("Ada Lovelace", "1 Main St", "", "Pune", "MH", "411001", "IN");

    @Test
    void createsANormalizedAccountWithOnlyABcryptPasswordHash() {
        var account = service.register("Ada.L", "safe-password-123", "Ada Lovelace", "ada@example.test", "123", address,
                "en", "CATS", true, false);

        assertThat(account.username()).isEqualTo("ada.l");
        assertThat(account.passwordHash()).doesNotContain("safe-password-123");
        assertThat(passwords.matches("safe-password-123", account.passwordHash())).isTrue();
    }

    @Test
    void rejectsADuplicateUsername() {
        service.register("ada", "safe-password-123", "Ada", "ada@example.test", "123", address, "en", "CATS", true, true);

        assertThatThrownBy(() -> service.register("ADA", "another-password", "Ada", "other@example.test", "123", address,
                "en", "CATS", true, true)).isInstanceOf(AccountAlreadyExistsException.class);
    }

    @Test
    void updatesProfileWithoutReplacingThePasswordHash() {
        var created = service.register("ada", "safe-password-123", "Ada", "ada@example.test", "123", address, "en", "CATS", true, true);
        var updated = service.update("ada", "Ada Byron", "ada@new.test", "456", address, "fr", "DOGS", false, false);

        assertThat(updated.fullName()).isEqualTo("Ada Byron");
        assertThat(updated.passwordHash()).isEqualTo(created.passwordHash());
        assertThat(updated.preferredLanguage()).isEqualTo("fr");
    }
}
