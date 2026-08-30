package com.mongodb.modernization.petstore.orders.application;

public class DuplicateCheckoutException extends RuntimeException {
    /** Creates a duplicate checkout exception and wires its required collaborators. */
    public DuplicateCheckoutException(Throwable cause) { super("Checkout idempotency key already exists", cause); }
}
