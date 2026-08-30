package com.mongodb.modernization.petstore.cart.domain;

public class CartLineNotFoundException extends RuntimeException {
    /** Creates a cart line not found exception and wires its required collaborators. */
    public CartLineNotFoundException(String productId) {
        super("Product " + productId + " is not in the cart");
    }
}
