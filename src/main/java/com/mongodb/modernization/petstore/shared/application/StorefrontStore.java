package com.mongodb.modernization.petstore.shared.application;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.domain.Address;

import java.util.List;
import java.util.Optional;

public interface StorefrontStore {
    List<Product> products();
    Optional<Product> product(String productId);
    Cart cart(String customerId);
    Cart addToCart(String customerId, long expectedVersion, String productId, int quantity);
    Cart updateCart(String customerId, long expectedVersion, String productId, int quantity);
    Cart removeFromCart(String customerId, long expectedVersion, String productId);
    Order checkout(String customerId, long expectedCartVersion, String idempotencyKey, Address address);
    Optional<Order> orderByIdempotencyKey(String customerId, String idempotencyKey);
    List<Order> orders(String customerId);
    void seedIfEmpty();
}
