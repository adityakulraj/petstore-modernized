package com.mongodb.modernization.petstore.shared.application;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.application.DuplicateCheckoutException;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StorefrontService implements ApplicationRunner {
    private final StorefrontStore store;

    public StorefrontService(StorefrontStore store) { this.store = store; }

    public List<Product> products() { return store.products(); }
    public Product product(String id) { return store.product(id).orElseThrow(() -> new NotFoundException("Unknown product " + id)); }
    public Cart cart(String customerId) { return store.cart(customerId); }
    public Cart add(String customerId, long version, String productId, int quantity) {
        return store.addToCart(customerId, version, productId, quantity);
    }
    public Cart update(String customerId, long version, String productId, int quantity) {
        return store.updateCart(customerId, version, productId, quantity);
    }
    public Cart remove(String customerId, long version, String productId) {
        return store.removeFromCart(customerId, version, productId);
    }
    public Order checkout(String customerId, long version, String key, Address address) {
        return store.orderByIdempotencyKey(customerId, key).orElseGet(() -> {
            try {
                return store.checkout(customerId, version, key, address);
            } catch (DuplicateCheckoutException duplicate) {
                return store.orderByIdempotencyKey(customerId, key).orElseThrow(() -> duplicate);
            }
        });
    }
    public List<Order> orders(String customerId) { return store.orders(customerId); }

    @Override public void run(ApplicationArguments args) { store.seedIfEmpty(); }
}
