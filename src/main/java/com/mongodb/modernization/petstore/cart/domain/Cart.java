package com.mongodb.modernization.petstore.cart.domain;

import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public record Cart(String id, String customerId, long version, List<CartLine> lines) {
    public Cart {
        lines = List.copyOf(lines);
    }

    public static Cart empty(String id, String customerId, long version) {
        return new Cart(id, customerId, version, List.of());
    }

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

    public Cart remove(String productId) {
        var next = lines.stream().filter(line -> !line.productId().equals(productId)).toList();
        if (next.size() == lines.size()) {
            throw new CartLineNotFoundException(productId);
        }
        return new Cart(id, customerId, version, next);
    }

    public BigDecimal total() {
        return lines.stream().map(CartLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_EVEN);
    }
}
