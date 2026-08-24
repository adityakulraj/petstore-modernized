package com.mongodb.modernization.petstore.persistence;

import com.mongodb.modernization.petstore.orders.application.InsufficientStockException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class StorefrontStoreContract {
    @Autowired protected StorefrontStore store;
    private static final Address ADDRESS = new Address("Alice", "100 Modernization Way", "", "Pune",
            "Maharashtra", "411001", "India");

    @Test @Order(1)
    void catalogIsSeededAndCartUsesOptimisticVersioning() {
        assertThat(store.products()).hasSizeGreaterThanOrEqualTo(7);
        var customer = unique("version");
        var initial = store.cart(customer);
        var changed = store.addToCart(customer, initial.version(), "FI-SW-01", 1);

        assertThat(changed.version()).isGreaterThan(initial.version());
        assertThatThrownBy(() -> store.updateCart(customer, initial.version(), "FI-SW-01", 2))
                .isInstanceOf(StoreConflictException.class);
    }

    @Test @Order(2)
    void checkoutIsAtomicAndIdempotent() {
        var customer = unique("idem");
        var cart = store.cart(customer);
        cart = store.addToCart(customer, cart.version(), "FI-SW-02", 2);
        var first = store.checkout(customer, cart.version(), "checkout-key", ADDRESS);
        var repeated = store.checkout(customer, cart.version(), "checkout-key", ADDRESS);

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(store.cart(customer).lines()).isEmpty();
        assertThat(store.orders(customer)).extracting(order -> order.id()).containsExactly(first.id());
    }

    @Test @Order(3)
    void simultaneousCartChangesDoNotLoseAnUpdate() throws Exception {
        var customer = unique("cart-race");
        var initial = store.addToCart(customer, store.cart(customer).version(), "AV-CB-01", 1);
        var gate = new CountDownLatch(1);
        var success = new AtomicInteger();
        var conflicts = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int quantity : new int[]{2, 3}) executor.submit(() -> {
                await(gate);
                try { store.updateCart(customer, initial.version(), "AV-CB-01", quantity); success.incrementAndGet(); }
                catch (StoreConflictException expected) { conflicts.incrementAndGet(); }
            });
            gate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(success).hasValue(1);
        assertThat(conflicts).hasValue(1);
    }

    @Test @Order(4)
    void simultaneousBuyersCannotMakeInventoryNegative() throws Exception {
        var customerA = unique("buyer-a"); var customerB = unique("buyer-b");
        var cartA = store.addToCart(customerA, store.cart(customerA).version(), "K9-BD-01", 4);
        var cartB = store.addToCart(customerB, store.cart(customerB).version(), "K9-BD-01", 4);
        var gate = new CountDownLatch(1);
        var placed = new AtomicInteger(); var soldOut = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> checkoutOutcome(customerA, cartA.version(), gate, placed, soldOut));
            executor.submit(() -> checkoutOutcome(customerB, cartB.version(), gate, placed, soldOut));
            gate.countDown(); executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(placed).hasValue(1);
        assertThat(soldOut).hasValue(1);
        assertThat(store.product("K9-BD-01").orElseThrow().stock()).isZero();
    }

    private void checkoutOutcome(String customer, long version, CountDownLatch gate,
                                 AtomicInteger placed, AtomicInteger soldOut) {
        await(gate);
        try { store.checkout(customer, version, UUID.randomUUID().toString(), ADDRESS); placed.incrementAndGet(); }
        catch (InsufficientStockException expected) { soldOut.incrementAndGet(); }
    }
    private static void await(CountDownLatch gate) {
        try { gate.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new RuntimeException(interrupted); }
    }
    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
}
