package com.mongodb.modernization.petstore.accounts.application;

import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CustomerAccountService {
    private static final Logger LOG = LoggerFactory.getLogger(CustomerAccountService.class);
    private final CustomerAccountStore store;
    private final PasswordEncoder passwords;

    public CustomerAccountService(CustomerAccountStore store, PasswordEncoder passwords) {
        this.store = store; this.passwords = passwords;
    }

    public CustomerAccount register(String username, String password, String fullName, String email, String phone,
                                    Address address, String language, String favoriteCategory,
                                    boolean myListPreference, boolean bannerPreference) {
        var normalized = username.trim().toLowerCase(Locale.ROOT);
        if (store.account(normalized).isPresent()) throw new AccountAlreadyExistsException(normalized);
        var account = store.save(new CustomerAccount(normalized, passwords.encode(password), fullName, email, phone, address,
                language, favoriteCategory, myListPreference, bannerPreference));
        LOG.atInfo().addKeyValue("event", "account.registered").addKeyValue("customerId", normalized)
                .addKeyValue("preferredLanguage", language).addKeyValue("favoriteCategory", favoriteCategory)
                .log("Customer account registered");
        return account;
    }

    public CustomerAccount account(String username) {
        return store.account(username).orElseThrow(() -> new NotFoundException("Customer account not found"));
    }

    public CustomerAccount update(String username, String fullName, String email, String phone, Address address,
                                  String language, String favoriteCategory, boolean myListPreference,
                                  boolean bannerPreference) {
        var account = store.save(account(username).withProfile(fullName, email, phone, address, language, favoriteCategory,
                myListPreference, bannerPreference));
        LOG.atInfo().addKeyValue("event", "account.profile.updated").addKeyValue("customerId", username)
                .addKeyValue("preferredLanguage", language).addKeyValue("favoriteCategory", favoriteCategory)
                .log("Customer account profile updated");
        return account;
    }
}
