package com.mongodb.modernization.petstore.cart.domain;

import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable cart-line price snapshot with bounded quantity. */
public record CartLine(String productId, String productName, BigDecimal unitPrice, int quantity) {
    public static final int MAX_QUANTITY = 99;

    /** Validates quantity and normalizes the unit price to currency scale. */
    public CartLine {
        if (quantity < 1 || quantity > MAX_QUANTITY) {
            throw new InvalidQuantityException(quantity);
        }
        unitPrice = unitPrice.setScale(2, RoundingMode.HALF_EVEN);
    }

    /** Creates a cart line from the selected product's current display name and price. */
    public static CartLine from(Product product, int quantity) {
        return new CartLine(product.id(), product.displayName(), product.price(), quantity);
    }

    /** Calculates the currency-rounded extended price for this quantity. */
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_EVEN);
    }

    /** Returns a validated copy with a replacement quantity. */
    public CartLine withQuantity(int value) {
        return new CartLine(productId, productName, unitPrice, value);
    }
}
