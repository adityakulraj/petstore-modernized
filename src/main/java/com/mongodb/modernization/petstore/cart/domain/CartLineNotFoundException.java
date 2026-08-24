package com.mongodb.modernization.petstore.cart.domain;

public class CartLineNotFoundException extends RuntimeException {
    public CartLineNotFoundException(String productId) {
        super("Product " + productId + " is not in the cart");
    }
}
