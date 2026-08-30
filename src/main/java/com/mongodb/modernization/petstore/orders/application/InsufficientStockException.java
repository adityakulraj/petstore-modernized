package com.mongodb.modernization.petstore.orders.application;

public class InsufficientStockException extends RuntimeException {
    /** Creates a insufficient stock exception and wires its required collaborators. */
    public InsufficientStockException(String productId) { super("Insufficient stock for product " + productId); }
}
