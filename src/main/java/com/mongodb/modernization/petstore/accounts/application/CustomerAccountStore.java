package com.mongodb.modernization.petstore.accounts.application;

import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;

import java.util.Optional;

public interface CustomerAccountStore {
    Optional<CustomerAccount> account(String username);
    CustomerAccount save(CustomerAccount account);
}
