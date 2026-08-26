package com.mongodb.modernization.petstore.cart.domain;

import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CartLine(String productId, String productName, BigDecimal unitPrice, int quantity) {
    public static final int MAX_QUANTITY = 99;

    public CartLine {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_EVEN);
    }

    public static CartLine from(Product product, int quantity) {
        return new CartLine(product.id(), product.displayName(), product.price(), quantity);
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_EVEN);
    }

    public CartLine withQuantity(int value) {
        return new CartLine(productId, productName, unitPrice, value);
    }
}
