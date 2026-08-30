package com.mongodb.modernization.petstore.shared.application;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.orders.application.DuplicateCheckoutException;
import com.mongodb.modernization.petstore.orders.application.CustomerOrderActionStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.payments.domain.Payment;
import com.mongodb.modernization.petstore.shared.domain.Address;
import com.mongodb.modernization.petstore.supplier.application.SupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StorefrontService implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(StorefrontService.class);
    private final StorefrontStore store;
    private final SupplierService supplier;
    private final CustomerOrderActionStore orderActions;
    private final PaymentStore payments;

    public StorefrontService(StorefrontStore store, SupplierService supplier,
                             CustomerOrderActionStore orderActions, PaymentStore payments) {
        this.store = store;
        this.supplier = supplier;
        this.orderActions = orderActions;
        this.payments = payments;
    }

    public List<Product> products() { return store.products(); }
    public List<Product> products(String categoryId) { return store.products(categoryId); }
    public Product product(String id) { return store.product(id).orElseThrow(() -> new NotFoundException("Unknown product " + id)); }
    public Cart cart(String customerId) { return store.cart(customerId); }
    public Cart add(String customerId, long version, String productId, int quantity) {
        var cart = store.addToCart(customerId, version, productId, quantity);
        logCartMutation("cart.item.added", customerId, productId, quantity, cart.version());
        return cart;
    }
    public Cart update(String customerId, long version, String productId, int quantity) {
        var cart = store.updateCart(customerId, version, productId, quantity);
        logCartMutation("cart.item.updated", customerId, productId, quantity, cart.version());
        return cart;
    }
    public Cart remove(String customerId, long version, String productId) {
        var cart = store.removeFromCart(customerId, version, productId);
        logCartMutation("cart.item.removed", customerId, productId, 0, cart.version());
        return cart;
    }
    public Order checkout(String customerId, long version, String key, Address address) {
        return checkout(customerId, version, key, address, Payment.APPROVED_DEMO_TOKEN);
    }
    public Order checkout(String customerId, long version, String key, Address address, String paymentToken) {
        var existing = store.orderByIdempotencyKey(customerId, key);
        if (existing.isPresent()) {
            logOrder("order.idempotency.replayed", existing.get());
            handOffApprovedOrder(existing.get());
            return existing.get();
        }
        Order order;
        var replayed = false;
        try {
            order = store.checkout(customerId, version, key, address, paymentToken);
        } catch (DuplicateCheckoutException duplicate) {
            // A concurrent request can win the unique idempotency-key insert after our first lookup.
            order = store.orderByIdempotencyKey(customerId, key).orElseThrow(() -> duplicate);
            replayed = true;
        }
        logOrder(replayed ? "order.idempotency.replayed" : "order.placed", order);
        // This durable, idempotent hand-off is attempted for both new orders and replays. If the
        // first hand-off is interrupted after checkout commits, retrying checkout repairs it.
        handOffApprovedOrder(order);
        return order;
    }
    public List<Order> orders(String customerId) { return store.orders(customerId); }
    public List<Payment> payments(String customerId) { return payments.payments(customerId); }

    public Order cancel(String customerId, String orderId, long expectedVersion, String key, String reason) {
        var order = orderActions.cancel(customerId, orderId, expectedVersion, key, reason);
        logCustomerAction("order.cancelled", order, key);
        return order;
    }

    public Order refund(String customerId, String orderId, long expectedVersion, String key, String reason) {
        var order = orderActions.refund(customerId, orderId, expectedVersion, key, reason);
        logCustomerAction("order.refunded", order, key);
        return order;
    }

    @Override public void run(ApplicationArguments args) {
        store.seedIfEmpty();
        LOG.atInfo().addKeyValue("event", "catalog.seed.checked").log("Catalog seed check completed");
    }

    private static void logCartMutation(String event, String customerId, String productId, int quantity, long version) {
        LOG.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("customerId", customerId)
                .addKeyValue("productId", productId)
                .addKeyValue("quantity", quantity)
                .addKeyValue("cartVersion", version)
                .log("Cart mutation completed");
    }

    private static void logOrder(String event, Order order) {
        LOG.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("orderId", order.id())
                .addKeyValue("customerId", order.customerId())
                .addKeyValue("lineCount", order.lines().size())
                .addKeyValue("total", order.total())
                .log("Order operation completed");
    }

    private void handOffApprovedOrder(Order order) {
        if (order.supplierReady()) supplier.ensurePurchaseOrder(order);
    }

    private static void logCustomerAction(String event, Order order, String key) {
        LOG.atInfo().addKeyValue("event", event).addKeyValue("orderId", order.id())
                .addKeyValue("customerId", order.customerId()).addKeyValue("orderVersion", order.version())
                .addKeyValue("idempotencyKey", key).log("Customer order action completed");
    }
}
