package com.mongodb.modernization.petstore.persistence;

import com.mongodb.modernization.petstore.orders.application.InsufficientStockException;
import com.mongodb.modernization.petstore.orders.application.AdminOrderService;
import com.mongodb.modernization.petstore.orders.application.AdminOrderStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import com.mongodb.modernization.petstore.shared.domain.Address;
import com.mongodb.modernization.petstore.supplier.application.SupplierStore;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class StorefrontStoreContract {
    @Autowired protected StorefrontStore store;
    @Autowired protected SupplierStore supplier;
    @Autowired protected AdminOrderService administrator;
    private static final Address ADDRESS = new Address("Alice", "100 Modernization Way", "", "Pune",
            "Maharashtra", "411001", "India");

    @Test @org.junit.jupiter.api.Order(1)
    void catalogIsSeededAndCartUsesOptimisticVersioning() {
        assertThat(store.products()).hasSizeGreaterThanOrEqualTo(7);
        var customer = unique("version");
        var initial = store.cart(customer);
        var changed = store.addToCart(customer, initial.version(), "FI-SW-01", 1);

        assertThat(changed.version()).isGreaterThan(initial.version());
        assertThatThrownBy(() -> store.updateCart(customer, initial.version(), "FI-SW-01", 2))
                .isInstanceOf(StoreConflictException.class);
    }

    @Test @org.junit.jupiter.api.Order(2)
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

    @Test @org.junit.jupiter.api.Order(3)
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

    @Test @org.junit.jupiter.api.Order(4)
    void simultaneousBuyersCannotMakeInventoryNegative() throws Exception {
        var customerA = unique("buyer-a"); var customerB = unique("buyer-b");
        var cartA = store.addToCart(customerA, store.cart(customerA).version(), "K9-BD-01", 4);
        var cartB = store.addToCart(customerB, store.cart(customerB).version(), "K9-BD-01", 4);
        var gate = new CountDownLatch(1);
        var placed = new AtomicInteger(); var soldOut = new AtomicInteger();
        var unexpected = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> checkoutOutcome(customerA, cartA.version(), gate, placed, soldOut, unexpected));
            executor.submit(() -> checkoutOutcome(customerB, cartB.version(), gate, placed, soldOut, unexpected));
            gate.countDown(); executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(unexpected.get()).isNull();
        assertThat(placed).hasValue(1);
        assertThat(soldOut).hasValue(1);
        assertThat(store.product("K9-BD-01").orElseThrow().stock()).isZero();
    }

    @Test @org.junit.jupiter.api.Order(5)
    void supplierInventoryPutIsReplaySafeAndRejectsStaleCompetingChanges() {
        var original = store.product("FI-SW-01").orElseThrow();
        var changed = supplier.replaceInventory(original.id(), original.version(), original.stock() + 3);
        var replay = supplier.replaceInventory(original.id(), original.version(), original.stock() + 3);

        assertThat(changed.stock()).isEqualTo(original.stock() + 3);
        assertThat(replay.version()).isEqualTo(changed.version());
        assertThatThrownBy(() -> supplier.replaceInventory(original.id(), original.version(), original.stock() + 4))
                .isInstanceOf(StoreConflictException.class);
    }

    @Test @org.junit.jupiter.api.Order(6)
    void supplierPurchaseOrderCreationAndProcessingAreIdempotent() {
        var customer = unique("supplier-idem");
        var cart = store.addToCart(customer, store.cart(customer).version(), "FL-DSH-01", 1);
        var order = store.checkout(customer, cart.version(), "supplier-key", ADDRESS);

        var first = supplier.ensurePurchaseOrder(order);
        var repeated = supplier.ensurePurchaseOrder(order);
        var processed = supplier.processPurchaseOrder(first.id(), first.version());
        var processReplay = supplier.processPurchaseOrder(first.id(), first.version());

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(processed.status()).isEqualTo(SupplierPurchaseOrder.Status.PROCESSED);
        assertThat(processReplay.id()).isEqualTo(processed.id());
        assertThat(processReplay.version()).isEqualTo(processed.version());
        assertThat(store.orders(customer)).extracting(orderView -> orderView.status()).containsExactly("COMPLETED");
    }

    @Test @org.junit.jupiter.api.Order(7)
    void simultaneousSupplierProcessingConvergesOnOneCompletedTransition() throws Exception {
        var customer = unique("supplier-race");
        var cart = store.addToCart(customer, store.cart(customer).version(), "RP-IG-01", 1);
        var order = store.checkout(customer, cart.version(), "supplier-race-key", ADDRESS);
        var purchaseOrder = supplier.ensurePurchaseOrder(order);
        var gate = new CountDownLatch(1);
        var completed = new AtomicInteger();
        var unexpected = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int ignored = 0; ignored < 2; ignored++) executor.submit(() -> {
                await(gate);
                try {
                    var result = supplier.processPurchaseOrder(purchaseOrder.id(), purchaseOrder.version());
                    if (result.status() == SupplierPurchaseOrder.Status.PROCESSED) completed.incrementAndGet();
                } catch (Throwable failure) { unexpected.compareAndSet(null, failure); }
            });
            gate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally { executor.shutdownNow(); }

        assertThat(unexpected.get()).isNull();
        assertThat(completed).hasValue(2);
        var stored = supplier.purchaseOrders().stream().filter(po -> po.id().equals(purchaseOrder.id())).findFirst().orElseThrow();
        assertThat(stored.status()).isEqualTo(SupplierPurchaseOrder.Status.PROCESSED);
        assertThat(stored.version()).isPositive();
        var replay = supplier.processPurchaseOrder(stored.id(), purchaseOrder.version());
        assertThat(replay.version()).isEqualTo(stored.version());
        assertThat(store.orders(customer)).extracting(orderView -> orderView.status()).containsExactly("COMPLETED");
    }

    @Test @org.junit.jupiter.api.Order(8)
    void simultaneousCheckoutRetriesReturnTheSameOrderAndDecrementOnce() throws Exception {
        var customer = unique("checkout-retry");
        var productBefore = store.product("AV-CB-01").orElseThrow();
        var cart = store.addToCart(customer, store.cart(customer).version(), productBefore.id(), 1);
        var gate = new CountDownLatch(1);
        var first = new AtomicReference<com.mongodb.modernization.petstore.orders.domain.Order>();
        var second = new AtomicReference<com.mongodb.modernization.petstore.orders.domain.Order>();
        var unexpected = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> checkoutRetry(customer, cart.version(), gate, first, unexpected));
            executor.submit(() -> checkoutRetry(customer, cart.version(), gate, second, unexpected));
            gate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally { executor.shutdownNow(); }

        assertThat(unexpected.get()).isNull();
        assertThat(first.get()).isNotNull();
        assertThat(second.get()).isNotNull();
        assertThat(second.get().id()).isEqualTo(first.get().id());
        assertThat(store.product(productBefore.id()).orElseThrow().stock()).isEqualTo(productBefore.stock() - 1);
        assertThat(store.orders(customer)).hasSize(1);
    }

    @Test @org.junit.jupiter.api.Order(9)
    void highValueOrdersRequireReplaySafeApprovalOrDenial() {
        var product = store.product("K9-RT-01").orElseThrow();
        var approvedCustomer = unique("admin-approve");
        var approvedCart = store.addToCart(approvedCustomer, store.cart(approvedCustomer).version(), product.id(), 1);
        var pending = store.checkout(approvedCustomer, approvedCart.version(), "admin-approve-key", ADDRESS);

        assertThat(pending.status()).isEqualTo(Order.PENDING);
        assertThat(supplier.purchaseOrders()).noneMatch(po -> po.orderId().equals(pending.id()));
        assertThat(store.product(product.id()).orElseThrow().stock()).isEqualTo(product.stock() - 1);

        var approved = administrator.review(pending.id(), pending.version(), AdminOrderStore.Decision.APPROVED, "admin");
        var replay = administrator.review(pending.id(), pending.version(), AdminOrderStore.Decision.APPROVED, "admin");
        assertThat(approved.status()).isEqualTo(Order.APPROVED);
        assertThat(approved.version()).isEqualTo(pending.version() + 1);
        assertThat(approved.reviewedBy()).isEqualTo("admin");
        assertThat(replay.version()).isEqualTo(approved.version());
        assertThat(supplier.purchaseOrders()).filteredOn(po -> po.orderId().equals(pending.id())).hasSize(1);
        assertThatThrownBy(() -> administrator.review(pending.id(), pending.version(), AdminOrderStore.Decision.DENIED, "admin"))
                .isInstanceOf(StoreConflictException.class);

        var deniedCustomer = unique("admin-deny");
        var beforeDeniedCheckout = store.product(product.id()).orElseThrow();
        var deniedCart = store.addToCart(deniedCustomer, store.cart(deniedCustomer).version(), product.id(), 1);
        var toDeny = store.checkout(deniedCustomer, deniedCart.version(), "admin-deny-key", ADDRESS);
        var denied = administrator.review(toDeny.id(), toDeny.version(), AdminOrderStore.Decision.DENIED, "admin");
        var deniedReplay = administrator.review(toDeny.id(), toDeny.version(), AdminOrderStore.Decision.DENIED, "admin");

        assertThat(denied.status()).isEqualTo(Order.DENIED);
        assertThat(deniedReplay.version()).isEqualTo(denied.version());
        assertThat(store.product(product.id()).orElseThrow().stock()).isEqualTo(beforeDeniedCheckout.stock());
        assertThat(supplier.purchaseOrders()).noneMatch(po -> po.orderId().equals(toDeny.id()));
    }

    @Test @org.junit.jupiter.api.Order(10)
    void competingApprovalAndDenialHaveOneWinnerAndCoherentInventory() throws Exception {
        var product = store.product("K9-RT-01").orElseThrow();
        var customer = unique("admin-race");
        var cart = store.addToCart(customer, store.cart(customer).version(), product.id(), 1);
        var pending = store.checkout(customer, cart.version(), "admin-race-key", ADDRESS);
        var gate = new CountDownLatch(1);
        var success = new AtomicInteger();
        var conflicts = new AtomicInteger();
        var winner = new AtomicReference<com.mongodb.modernization.petstore.orders.domain.Order>();
        var unexpected = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (var decision : AdminOrderStore.Decision.values()) executor.submit(() -> {
                await(gate);
                try {
                    winner.set(administrator.review(pending.id(), pending.version(), decision, "admin"));
                    success.incrementAndGet();
                } catch (StoreConflictException expected) { conflicts.incrementAndGet(); }
                catch (Throwable failure) { unexpected.compareAndSet(null, failure); }
            });
            gate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally { executor.shutdownNow(); }

        assertThat(unexpected.get()).isNull();
        assertThat(success).hasValue(1);
        assertThat(conflicts).hasValue(1);
        assertThat(winner.get().status()).isIn(Order.APPROVED, Order.DENIED);
        int expectedStock = winner.get().status().equals(Order.DENIED) ? product.stock() : product.stock() - 1;
        assertThat(store.product(product.id()).orElseThrow().stock()).isEqualTo(expectedStock);
        assertThat(supplier.purchaseOrders().stream().filter(po -> po.orderId().equals(pending.id())).count())
                .isEqualTo(winner.get().status().equals(Order.APPROVED) ? 1 : 0);
    }

    private void checkoutOutcome(String customer, long version, CountDownLatch gate,
                                 AtomicInteger placed, AtomicInteger soldOut, AtomicReference<Throwable> unexpected) {
        await(gate);
        try { store.checkout(customer, version, UUID.randomUUID().toString(), ADDRESS); placed.incrementAndGet(); }
        catch (InsufficientStockException expected) { soldOut.incrementAndGet(); }
        catch (Throwable failure) { unexpected.compareAndSet(null, failure); }
    }
    private void checkoutRetry(String customer, long version, CountDownLatch gate,
                               AtomicReference<com.mongodb.modernization.petstore.orders.domain.Order> result,
                               AtomicReference<Throwable> unexpected) {
        await(gate);
        try { result.set(store.checkout(customer, version, "same-retry-key", ADDRESS)); }
        catch (Throwable failure) { unexpected.compareAndSet(null, failure); }
    }
    private static void await(CountDownLatch gate) {
        try { gate.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new RuntimeException(interrupted); }
    }
    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
}
