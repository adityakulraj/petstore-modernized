package com.mongodb.modernization.petstore.cart.domain;

import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Immutable customer-cart snapshot protected by an optimistic-lock version. */
public record Cart(String id, String customerId, long version, List<CartLine> lines) {
    /** Defensively copies cart lines so callers cannot mutate a persisted snapshot. */
    public Cart {
        lines = List.copyOf(lines);
    }

    /** Creates an empty cart at the supplied persistence version. */
    public static Cart empty(String id, String customerId, long version) {
        return new Cart(id, customerId, version, List.of());
    }

    /** Adds a product or increments its existing quantity while enforcing quantity limits. */
    public Cart add(Product product, int quantity) {
        if (quantity < 1 || quantity > CartLine.MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
        var next = new ArrayList<>(lines);
        for (int i = 0; i < next.size(); i++) {
            var existing = next.get(i);
            if (existing.productId().equals(product.id())) {
                next.set(i, existing.withQuantity(Math.addExact(existing.quantity(), quantity)));
                return new Cart(id, customerId, version, next);
            }
        }
        next.add(CartLine.from(product, quantity));
        return new Cart(id, customerId, version, next);
    }

    /** Replaces the quantity of an existing line and rejects unknown products. */
    public Cart update(String productId, int quantity) {
        var next = new ArrayList<>(lines);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).productId().equals(productId)) {
                next.set(i, next.get(i).withQuantity(quantity));
                return new Cart(id, customerId, version, next);
            }
        }
        throw new CartLineNotFoundException(productId);
    }

    /** Removes an existing line and rejects requests for products not present in the cart. */
    public Cart remove(String productId) {
        var next = lines.stream().filter(line -> !line.productId().equals(productId)).toList();
        if (next.size() == lines.size()) {
            throw new CartLineNotFoundException(productId);
        }
        return new Cart(id, customerId, version, next);
    }

    /** Calculates the currency-rounded total from immutable line-price snapshots. */
    public BigDecimal total() {
        return lines.stream().map(CartLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }
}
