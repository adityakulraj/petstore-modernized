package com.mongodb.modernization.petstore.accounts.application;

import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;

import java.util.Optional;

public interface CustomerAccountStore {
    /** Executes the account persistence operation against the selected database. */
    Optional<CustomerAccount> account(String username);
    /** Validates and persists . */
    CustomerAccount save(CustomerAccount account);
}
