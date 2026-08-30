package com.mongodb.modernization.petstore.cart.domain;

public class InvalidQuantityException extends RuntimeException {
    /** Creates a invalid quantity exception and wires its required collaborators. */
    public InvalidQuantityException(int quantity) {
        super("Quantity must be between 1 and " + CartLine.MAX_QUANTITY + "; received " + quantity);
    }
}
