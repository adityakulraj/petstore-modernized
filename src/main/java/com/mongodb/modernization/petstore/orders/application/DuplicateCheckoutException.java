package com.mongodb.modernization.petstore.orders.application;

public class DuplicateCheckoutException extends RuntimeException {
    public DuplicateCheckoutException(Throwable cause) { super("Checkout idempotency key already exists", cause); }
}
