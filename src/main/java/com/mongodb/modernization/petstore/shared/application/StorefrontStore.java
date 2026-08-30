package com.mongodb.modernization.petstore.shared.application;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.domain.Payment;
import com.mongodb.modernization.petstore.shared.domain.Address;

import java.util.List;
import java.util.Optional;

public interface StorefrontStore {
    /** Executes the products persistence operation against the selected database. */
    default List<Product> products() { return products(null); }
    /** Executes the products persistence operation against the selected database. */
    List<Product> products(String categoryId);
    /** Executes the product persistence operation against the selected database. */
    Optional<Product> product(String productId);
    /** Executes the cart persistence operation against the selected database. */
    Cart cart(String customerId);
    /** Executes the add to cart persistence operation against the selected database. */
    Cart addToCart(String customerId, long expectedVersion, String productId, int quantity);
    /** Updates cart while enforcing the applicable validation and concurrency rules. */
    Cart updateCart(String customerId, long expectedVersion, String productId, int quantity);
    /** Removes from cart while preserving consistency. */
    Cart removeFromCart(String customerId, long expectedVersion, String productId);
    /** Executes the checkout persistence operation against the selected database. */
    Order checkout(String customerId, long expectedCartVersion, String idempotencyKey, Address address);
    /** Executes the checkout persistence operation against the selected database. */
    default Order checkout(String customerId, long expectedCartVersion, String idempotencyKey, Address address,
                           String paymentToken) {
        return checkout(customerId, expectedCartVersion, idempotencyKey, address);
    }
    /** Executes the order by idempotency key persistence operation against the selected database. */
    Optional<Order> orderByIdempotencyKey(String customerId, String idempotencyKey);
    /** Executes the orders persistence operation against the selected database. */
    List<Order> orders(String customerId);
    /** Seeds the initial catalog without overwriting existing product data. */
    void seedIfEmpty();
}
