package com.mongodb.modernization.petstore.cart.domain;

public class InvalidQuantityException extends RuntimeException {
    public InvalidQuantityException(int quantity) {
        super("Quantity must be between 1 and " + CartLine.MAX_QUANTITY + "; received " + quantity);
    }
}
