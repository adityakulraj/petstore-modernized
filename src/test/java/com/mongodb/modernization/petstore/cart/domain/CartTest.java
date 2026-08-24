package com.mongodb.modernization.petstore.cart.domain;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {
    private static final Product FISH = new Product("fish", "FISH", "Fish", "Angelfish",
            "Freshwater fish", new BigDecimal("16.50"), 20, 0);

    @Test
    void combinesDuplicateProductsAndCalculatesMoneyExactly() {
        var cart = Cart.empty("c1", "alice", 0).add(FISH, 1).add(FISH, 2);

        assertThat(cart.lines()).singleElement().satisfies(line -> assertThat(line.quantity()).isEqualTo(3));
        assertThat(cart.total()).isEqualByComparingTo("49.50");
    }

    @Test
    void rejectsQuantitiesOutsideTheBusinessBoundary() {
        assertThatThrownBy(() -> Cart.empty("c1", "alice", 0).add(FISH, 0))
                .isInstanceOf(InvalidQuantityException.class);
        assertThatThrownBy(() -> Cart.empty("c1", "alice", 0).add(FISH, 100))
                .isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void preventsOverflowWhenAddingToAnExistingLine() {
        var cart = Cart.empty("c1", "alice", 0).add(FISH, 99);
        assertThatThrownBy(() -> cart.add(FISH, 1)).isInstanceOf(InvalidQuantityException.class);
    }

    @Test
    void updateAndRemoveRequireAnExistingLine() {
        var empty = Cart.empty("c1", "alice", 0);
        assertThatThrownBy(() -> empty.update("missing", 1)).isInstanceOf(CartLineNotFoundException.class);
        assertThatThrownBy(() -> empty.remove("missing")).isInstanceOf(CartLineNotFoundException.class);
    }
}
