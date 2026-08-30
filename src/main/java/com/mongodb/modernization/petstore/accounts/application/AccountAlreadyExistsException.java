package com.mongodb.modernization.petstore.accounts.application;

public class AccountAlreadyExistsException extends RuntimeException {
    /** Creates a account already exists exception and wires its required collaborators. */
    public AccountAlreadyExistsException(String username) { super("Username '" + username + "' is already in use"); }
}
