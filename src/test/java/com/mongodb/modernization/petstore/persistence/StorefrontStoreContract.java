package com.mongodb.modernization.petstore.persistence;

import com.mongodb.modernization.petstore.analytics.application.SalesAnalyticsService;
import com.mongodb.modernization.petstore.catalog.application.CatalogService;
import com.mongodb.modernization.petstore.catalog.application.CatalogStore;
import com.mongodb.modernization.petstore.orders.application.AdminOrderService;
import com.mongodb.modernization.petstore.orders.application.AdminOrderStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.mylist.application.MyListStore;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;
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
    @Autowired protected CustomerNotificationStore notifications;
    @Autowired protected MyListStore myLists;
    @Autowired protected SalesAnalyticsService salesAnalytics;
    @Autowired protected CatalogService catalog;
    @Autowired protected CatalogStore catalogStore;
    private static final Address ADDRESS = new Address("Alice", "100 Modernization Way", "", "Pune",
            "Maharashtra", "411001", "India");

    @Test @org.junit.jupiter.api.Order(1)
    void catalogIsSeededAndCartUsesOptimisticVersioning() {
        assertThat(store.products()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(store.product("K9-BD-01").orElseThrow()).satisfies(item -> {
            assertThat(item.productGroupId()).isEqualTo("K9-BD");
            assertThat(item.variantName()).isEqualTo("Male Adult");
        });
        assertThat(store.product("K9-BD-02").orElseThrow().variantName()).isEqualTo("Female Puppy");
        var customer = unique("version");
        var initial = store.cart(customer);
        var changed = store.addToCart(customer, initial.version(), "FI-SW-01", 1);

        assertThat(changed.version()).isGreaterThan(initial.version());
        assertThatThrownBy(() -> store.updateCart(customer, initial.version(), "FI-SW-01", 2))
                .isInstanceOf(StoreConflictException.class);
    }

    @Test @org.junit.jupiter.api.Order(14)
    void simultaneousFavoriteRetriesConvergeAndRemovalIsReplaySafe() throws Exception {
        var customer = unique("my-list-race");
        var gate = new CountDownLatch(1);
        var unexpected = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int ignored = 0; ignored < 2; ignored++) executor.submit(() -> {
                await(gate);
                try { myLists.add(customer, "K9-BD-01", Instant.now()); }
                catch (Throwable failure) { unexpected.compareAndSet(null, failure); }
            });
            gate.countDown(); executor.shutdown();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally { executor.shutdownNow(); }

        assertThat(unexpected.get()).isNull();
        assertThat(myLists.favorites(customer)).extracting(item -> item.itemId()).containsExactly("K9-BD-01");
        myLists.remove(customer, "K9-BD-01");
        myLists.remove(customer, "K9-BD-01");
        assertThat(myLists.favorites(customer)).isEmpty();
    }

    @Test @org.junit.jupiter.api.Order(15)
    void salesAnalyticsReadsRecognizedRevenueFromTheSelectedStore() {
        var today = LocalDate.now(ZoneOffset.UTC);
        var canary = store.product("AV-CB-01").orElseThrow();
        supplier.replaceInventory(canary.id(), canary.version(), canary.stock() + 1,
                unique("analytics-inventory"));
        var before = salesAnalytics.report(today, today, "BIRDS");
        var customer = unique("analytics");
        var cart = store.addToCart(customer, store.cart(customer).version(), canary.id(), 1);
        store.checkout(customer, cart.version(), "analytics-key", ADDRESS);

        var after = salesAnalytics.report(today, today, "BIRDS");
        assertThat(after.dimension()).isEqualTo("ITEM");
        assertThat(after.summary().acceptedOrders()).isEqualTo(before.summary().acceptedOrders() + 1);
        assertThat(after.summary().unitsSold()).isEqualTo(before.summary().unitsSold() + 1);
        assertThat(after.summary().revenue().subtract(before.summary().revenue())).isEqualByComparingTo("125.00");
        assertThat(after.breakdown()).anySatisfy(item -> {
            assertThat(item.key()).isEqualTo("AV-CB-01");
            assertThat(item.label()).isEqualTo("Canary");
        });
    }

    @Test @org.junit.jupiter.api.Order(16)
    void catalogCreationAndPriceChangesAreReplaySafeAuditedAndPreserveSnapshots() {
        var suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        var id = "CT-" + suffix;
        var created = catalog.create(id, "CT-GROUP", "Female Adult", "CATS", "Cats", "Siamese",
                "A catalog-managed item", new java.math.BigDecimal("425.00"), true, "admin");
        var replay = catalog.create(id, "CT-GROUP", "Female Adult", "CATS", "Cats", "Siamese",
                "A catalog-managed item", new java.math.BigDecimal("425.00"), true, "admin");
        assertThat(replay.version()).isEqualTo(created.version());
        assertThat(created.stock()).isZero();

        var customer = unique("catalog-price-snapshot");
        supplier.replaceInventory(id, created.version(), 2, unique("catalog-stock"));
        var stocked = catalogStore.product(id).orElseThrow();
        var cart = store.addToCart(customer, store.cart(customer).version(), id, 1);
        var updated = catalog.update(id, stocked.version(), "CT-GROUP", "Female Adult", "CATS", "Cats", "Siamese",
                "A catalog-managed item", new java.math.BigDecimal("450.00"), true, "admin");
        var updateReplay = catalog.update(id, stocked.version(), "CT-GROUP", "Female Adult", "CATS", "Cats", "Siamese",
                "A catalog-managed item", new java.math.BigDecimal("450.00"), true, "admin");

        assertThat(updateReplay.version()).isEqualTo(updated.version());
        var order = store.checkout(customer, cart.version(), unique("catalog-checkout"), ADDRESS);
        assertThat(order.lines()).singleElement().satisfies(line ->
                assertThat(line.unitPrice()).isEqualByComparingTo("425.00"));
        assertThat(catalogStore.changes().stream().filter(change -> change.productId().equals(id))).hasSize(2);
        assertThatThrownBy(() -> catalog.update(id, stocked.version(), "CT-GROUP", "Female Adult", "CATS", "Cats",
                "Siamese", "Competing change", new java.math.BigDecimal("451.00"), true, "admin"))
                .isInstanceOf(StoreConflictException.class);
    }

    @Test @org.junit.jupiter.api.Order(17)
    void archivedItemsDisappearFromStorefrontButRemainAdministrativelyVisible() {
        var suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        var id = "AR-" + suffix;
        var created = catalog.create(id, "AR-GROUP", "Standard", "BIRDS", "Birds", "Finch",
                "Temporary listing", new java.math.BigDecimal("75.00"), true, "admin");
        var archived = catalog.update(id, created.version(), "AR-GROUP", "Standard", "BIRDS", "Birds", "Finch",
                "Temporary listing", new java.math.BigDecimal("75.00"), false, "admin");

        assertThat(archived.active()).isFalse();
        assertThat(store.product(id)).isEmpty();
        assertThat(store.products()).noneMatch(product -> product.id().equals(id));
        assertThat(catalogStore.product(id)).isPresent();
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
    void simultaneousBuyersReserveOrBackorderWithoutMakingInventoryNegative() throws Exception {
        var customerA = unique("buyer-a"); var customerB = unique("buyer-b");
        var cartA = store.addToCart(customerA, store.cart(customerA).version(), "K9-BD-01", 4);
        var cartB = store.addToCart(customerB, store.cart(customerB).version(), "K9-BD-01", 4);
        var gate = new CountDownLatch(1);
        var reserved = new AtomicInteger(); var backordered = new AtomicInteger();
        var unexpected = new AtomicReference<Throwable>();
        var executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> checkoutOutcome(customerA, cartA.version(), gate, reserved, backordered, unexpected));
            executor.submit(() -> checkoutOutcome(customerB, cartB.version(), gate, reserved, backordered, unexpected));
            gate.countDown(); executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }
        assertThat(unexpected.get()).isNull();
        assertThat(reserved).hasValue(1);
        assertThat(backordered).hasValue(1);
        assertThat(store.product("K9-BD-01").orElseThrow().stock()).isZero();
    }

    @Test @org.junit.jupiter.api.Order(5)
    void supplierInventoryPutIsReplaySafeAndRejectsStaleCompetingChanges() {
        var original = store.product("FI-SW-01").orElseThrow();
        var key = unique("inventory-command");
        var changed = supplier.replaceInventory(original.id(), original.version(), original.stock() + 3, key);
        var replay = supplier.replaceInventory(original.id(), original.version(), original.stock() + 3, key);

        assertThat(changed.stock()).isEqualTo(original.stock() + 3);
        assertThat(replay.version()).isEqualTo(changed.version());
        assertThatThrownBy(() -> supplier.replaceInventory(original.id(), original.version(), original.stock() + 4, key))
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

    @Test @org.junit.jupiter.api.Order(11)
    void customerNotificationTimelineIsDurableDeduplicatedAndReadSafe() {
        var customer = unique("notification");
        var cart = store.addToCart(customer, store.cart(customer).version(), "K9-RT-01", 1);
        var pending = store.checkout(customer, cart.version(), "notification-key", ADDRESS);
        store.checkout(customer, cart.version(), "notification-key", ADDRESS);

        assertThat(notifications.notifications(customer)).extracting(CustomerNotification::type)
                .containsExactly(CustomerNotification.Type.ORDER_PENDING);

        var approved = administrator.review(pending.id(), pending.version(), AdminOrderStore.Decision.APPROVED, "admin");
        administrator.review(pending.id(), pending.version(), AdminOrderStore.Decision.APPROVED, "admin");
        var purchaseOrder = supplier.purchaseOrders().stream()
                .filter(po -> po.orderId().equals(approved.id())).findFirst().orElseThrow();
        supplier.processPurchaseOrder(purchaseOrder.id(), purchaseOrder.version());
        supplier.processPurchaseOrder(purchaseOrder.id(), purchaseOrder.version());

        var timeline = notifications.notifications(customer);
        assertThat(timeline).extracting(CustomerNotification::type)
                .containsExactly(CustomerNotification.Type.ORDER_COMPLETED,
                        CustomerNotification.Type.ORDER_APPROVED, CustomerNotification.Type.ORDER_PENDING);
        assertThat(timeline).extracting(CustomerNotification::id).doesNotHaveDuplicates();

        var unread = timeline.getFirst();
        var read = notifications.markRead(customer, unread.id(), unread.version(), Instant.now());
        var replay = notifications.markRead(customer, unread.id(), unread.version(), Instant.now());
        assertThat(read.readAt()).isNotNull();
        assertThat(replay.version()).isEqualTo(read.version());
        assertThatThrownBy(() -> notifications.markRead("somebody-else", unread.id(), read.version(), Instant.now()))
                .isInstanceOf(com.mongodb.modernization.petstore.shared.application.NotFoundException.class);
    }

    @Test @org.junit.jupiter.api.Order(12)
    void backorderIsReleasedAtomicallyWhenSupplierReplenishesInventory() {
        var product = store.product("FI-SW-02").orElseThrow();
        int quantity = product.stock() + 1;
        var customer = unique("backorder");
        var cart = store.addToCart(customer, store.cart(customer).version(), product.id(), quantity);
        var order = store.checkout(customer, cart.version(), "backorder-key", ADDRESS);

        assertThat(order.status()).isEqualTo(Order.BACKORDERED);
        assertThat(store.product(product.id()).orElseThrow().stock()).isEqualTo(product.stock());
        assertThat(store.cart(customer).lines()).isEmpty();
        assertThat(supplier.backorders()).extracting(Order::id).contains(order.id());
        assertThat(notifications.notifications(customer)).extracting(CustomerNotification::type)
                .containsExactly(CustomerNotification.Type.ORDER_BACKORDERED);

        var replenished = supplier.replaceInventory(product.id(), product.version(), quantity,
                unique("backorder-release"));
        var released = store.orders(customer).getFirst();
        assertThat(replenished.stock()).isZero();
        assertThat(released.status()).isEqualTo(Order.APPROVED);
        assertThat(supplier.backorders()).extracting(Order::id).doesNotContain(order.id());
        assertThat(supplier.purchaseOrders()).filteredOn(po -> po.orderId().equals(order.id())).hasSize(1);
        assertThat(notifications.notifications(customer)).extracting(CustomerNotification::type)
                .contains(CustomerNotification.Type.ORDER_BACKORDERED,
                        CustomerNotification.Type.ORDER_INVENTORY_ALLOCATED,
                        CustomerNotification.Type.ORDER_APPROVED);
    }

    @Test @org.junit.jupiter.api.Order(13)
    void concurrentReplenishmentRetriesReleaseOneBackorderExactlyOnce() throws Exception {
        var product = store.product("AV-CB-01").orElseThrow();
        int quantity = product.stock() + 1;
        var customer = unique("backorder-race");
        var cart = store.addToCart(customer, store.cart(customer).version(), product.id(), quantity);
        var order = store.checkout(customer, cart.version(), "backorder-race-key", ADDRESS);
        assertThat(order.status()).isEqualTo(Order.BACKORDERED);

        var gate = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var unexpected = new AtomicReference<Throwable>();
        var commandKey = unique("inventory-retry");
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int ignored = 0; ignored < 2; ignored++) executor.submit(() -> {
                await(gate);
                try {
                    supplier.replaceInventory(product.id(), product.version(), quantity, commandKey);
                    successes.incrementAndGet();
                } catch (Throwable failure) { unexpected.compareAndSet(null, failure); }
            });
            gate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally { executor.shutdownNow(); }

        assertThat(unexpected.get()).isNull();
        assertThat(successes).hasValue(2);
        assertThat(store.product(product.id()).orElseThrow().stock()).isZero();
        assertThat(store.orders(customer).getFirst().status()).isEqualTo(Order.PENDING);
        assertThat(supplier.purchaseOrders()).noneMatch(po -> po.orderId().equals(order.id()));
        assertThat(notifications.notifications(customer)).extracting(CustomerNotification::type)
                .containsExactlyInAnyOrder(CustomerNotification.Type.ORDER_BACKORDERED,
                        CustomerNotification.Type.ORDER_INVENTORY_ALLOCATED,
                        CustomerNotification.Type.ORDER_PENDING);
    }

    private void checkoutOutcome(String customer, long version, CountDownLatch gate,
                                 AtomicInteger reserved, AtomicInteger backordered, AtomicReference<Throwable> unexpected) {
        await(gate);
        try {
            var order = store.checkout(customer, version, UUID.randomUUID().toString(), ADDRESS);
            if (Order.BACKORDERED.equals(order.status())) backordered.incrementAndGet();
            else reserved.incrementAndGet();
        }
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
