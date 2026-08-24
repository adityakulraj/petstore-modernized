package com.mongodb.modernization.petstore.orders.application;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productId) { super("Insufficient stock for product " + productId); }
}
