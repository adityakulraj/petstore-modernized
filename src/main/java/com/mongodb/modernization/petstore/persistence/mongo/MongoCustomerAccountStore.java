package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.accounts.application.CustomerAccountStore;
import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Profile("mongo")
class MongoCustomerAccountStore implements CustomerAccountStore {
    private final MongoCustomerAccountRepository accounts;
    private final DatabaseExecutor database;
    MongoCustomerAccountStore(MongoCustomerAccountRepository accounts, DatabaseExecutor database) { this.accounts = accounts; this.database = database; }
    @Override public Optional<CustomerAccount> account(String username) {
        return database.execute("account.by_username", true, () -> accounts.findById(username).map(CustomerAccountDocument::toDomain));
    }
    @Override public CustomerAccount save(CustomerAccount account) {
        return database.execute("account.save", false, () -> accounts.save(new CustomerAccountDocument(account)).toDomain());
    }
}
