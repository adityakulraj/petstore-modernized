package com.mongodb.modernization.petstore.accounts.application;

public class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException(String username) { super("Username '" + username + "' is already in use"); }
}
