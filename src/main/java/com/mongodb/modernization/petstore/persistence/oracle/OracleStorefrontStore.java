package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.catalog.application.SeedProducts;
import com.mongodb.modernization.petstore.config.AppProperties;
import com.mongodb.modernization.petstore.orders.application.DuplicateCheckoutException;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.payments.application.PaymentStore;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Repository
@Profile("oracle")
class OracleStorefrontStore implements StorefrontStore {
    private final JpaProductRepository products;
    private final JpaCartRepository carts;
    private final JpaOrderRepository orders;
    private final TransactionTemplate transactions;
    private final CustomerNotificationStore notifications;
    private final PaymentStore payments;
    private final DatabaseExecutor database;
    private final BigDecimal approvalThreshold;
    private final Clock clock = Clock.systemUTC();

    /** Creates the Oracle storefront adapter with JPA repositories and the shared database executor. */
    OracleStorefrontStore(JpaProductRepository products, JpaCartRepository carts, JpaOrderRepository orders,
                          PlatformTransactionManager transactionManager, DatabaseExecutor database,
                          AppProperties properties, CustomerNotificationStore notifications, PaymentStore payments) {
        this.products = products; this.carts = carts; this.orders = orders;
        this.transactions = new TransactionTemplate(transactionManager);
        this.notifications = notifications;
        this.payments = payments;
        this.database = database;
        this.approvalThreshold = properties.admin().approvalThreshold();
    }

    @Override
    /** Queries active products, pushes category filtering into Oracle, and returns stable ID ordering. */
    public List<Product> products(String categoryId) {
        boolean filtered = categoryId != null && !categoryId.isBlank();
        return database.execute(filtered ? "catalog.products.by_category" : "catalog.products.all", true,
                () -> (filtered ? products.findByCategoryIdOrderByIdAsc(categoryId.trim().toUpperCase(Locale.ROOT))
                        : products.findAll()).stream()
                .map(ProductJpaEntity::toDomain).filter(Product::active)
                .sorted(Comparator.comparing(Product::id)).toList());
    }

    @Override
    /** Finds one active product by SKU and hides archived catalog entries. */
    public Optional<Product> product(String productId) {
        return database.execute("catalog.product.by_id", true,
                () -> products.findById(productId).map(ProductJpaEntity::toDomain).filter(Product::active));
    }

    @Override
    /** Loads or race-safely creates the customer's versioned cart. */
    public Cart cart(String customerId) {
        return database.execute("cart.by_customer", true, () -> transactions.execute(ignored ->
                carts.findById(customerId).orElseGet(() -> carts.saveAndFlush(new CartJpaEntity(customerId))).toDomain()));
    }

    @Override @Transactional
    /** Adds a product to a cart inside an Oracle transaction using optimistic locking. */
    public Cart addToCart(String customerId, long expectedVersion, String productId, int quantity) {
        return database.execute("cart.add", false, () -> {
            var product = products.findById(productId).map(ProductJpaEntity::toDomain)
                    .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
            return mutateCart(customerId, expectedVersion, cart -> cart.add(product, quantity));
        });
    }

    @Override @Transactional
    /** Replaces an existing cart-line quantity using the caller's expected version. */
    public Cart updateCart(String customerId, long expectedVersion, String productId, int quantity) {
        return database.execute("cart.update", false,
                () -> mutateCart(customerId, expectedVersion, cart -> cart.update(productId, quantity)));
    }

    @Override @Transactional
    /** Removes a cart line using the caller's expected version. */
    public Cart removeFromCart(String customerId, long expectedVersion, String productId) {
        return database.execute("cart.remove", false,
                () -> mutateCart(customerId, expectedVersion, cart -> cart.remove(productId)));
    }

    /** Applies one cart transformation and converts concurrent flushes into an HTTP-level conflict. */
    private Cart mutateCart(String customerId, long expectedVersion, UnaryOperator<Cart> mutation) {
        try {
            var entity = carts.findById(customerId).orElseGet(() -> new CartJpaEntity(customerId));
            var current = entity.toDomain();
            requireVersion(current.version(), expectedVersion);
            entity.replaceWith(mutation.apply(current));
            return carts.saveAndFlush(entity).toDomain();
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException conflict) {
            throw new StoreConflictException("The cart changed in another request; refresh and retry", conflict);
        }
    }

    @Override
    /** Delegates legacy-compatible checkout to the approved opaque demo-payment token. */
    public Order checkout(String customerId, long expectedCartVersion, String key, Address address) {
        return checkout(customerId, expectedCartVersion, key, address,
                com.mongodb.modernization.petstore.payments.domain.Payment.APPROVED_DEMO_TOKEN);
    }

    @Override
    /** Runs replay-safe checkout in one Oracle transaction with bounded transient retries. */
    public Order checkout(String customerId, long expectedCartVersion, String key, Address address, String paymentToken) {
        return database.execute("orders.checkout", true,
                () -> transactions.execute(ignored -> checkoutOnce(customerId, expectedCartVersion, key, address, paymentToken)));
    }

    /** Executes one atomic checkout attempt, including inventory, order, payment, notification, and cart changes. */
    private Order checkoutOnce(String customerId, long expectedCartVersion, String key, Address address, String paymentToken) {
        // Serialize checkouts for one customer's cart, then re-check the idempotency key while
        // holding the row lock. A simultaneous retry waits for the winner and returns its order.
        var cartEntity = carts.findByIdForCheckout(customerId)
                .orElseThrow(() -> new StoreConflictException("Cart is empty"));
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
        if (existing.isPresent()) {
            var replay = existing.get().toDomain();
            payments.authorize(replay, paymentToken, replay.createdAt());
            enqueueCheckoutNotification(replay);
            notifications.enqueue(replay, CustomerNotification.Type.PAYMENT_AUTHORIZED, replay.createdAt().plusMillis(1));
            return replay;
        }
        var cart = cartEntity.toDomain();
        requireVersion(cart.version(), expectedCartVersion);
        if (cart.lines().isEmpty()) throw new StoreConflictException("Cart is empty");
        // Lock product rows in a stable order. The whole order is either reserved or backordered;
        // a multi-line checkout never consumes only the lines that happened to be available.
        var lockedProducts = cart.lines().stream().sorted(java.util.Comparator.comparing(line -> line.productId()))
                .map(line -> products.findByIdForUpdate(line.productId())
                        .orElseThrow(() -> new NotFoundException("Unknown product " + line.productId())))
                .toList();
        var quantities = cart.lines().stream().collect(java.util.stream.Collectors.toMap(
                line -> line.productId(), line -> line.quantity()));
        var inventoryAvailable = lockedProducts.stream()
                .allMatch(product -> product.stock >= quantities.get(product.id));
        if (inventoryAvailable) {
            lockedProducts.forEach(product -> product.reserveStock(quantities.get(product.id)));
            products.saveAllAndFlush(lockedProducts);
        }
        var now = Instant.now(clock);
        var order = inventoryAvailable
                ? Order.submitted(UUID.randomUUID().toString(), customerId, key, now, address, cart, approvalThreshold)
                : Order.backordered(UUID.randomUUID().toString(), customerId, key, now, address, cart);
        try {
            var persistedOrder = orders.saveAndFlush(new OrderJpaEntity(order));
            var persisted = persistedOrder.toDomain();
            payments.authorize(persisted, paymentToken, now);
            enqueueCheckoutNotification(persisted);
            notifications.enqueue(persisted, CustomerNotification.Type.PAYMENT_AUTHORIZED, now.plusMillis(1));
            cartEntity.replaceWith(Cart.empty(cart.id(), customerId, cart.version()));
            carts.saveAndFlush(cartEntity);
            // Hibernate owns the initial @Version value. Return that persisted value so the first
            // administrator/supplier command uses the same optimistic token as the database row.
            return persisted;
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateCheckoutException(duplicate);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new StoreConflictException("The cart changed during checkout; refresh and retry", conflict);
        }
    }

    /** Enqueues the notification that corresponds to the order state produced by checkout. */
    private void enqueueCheckoutNotification(Order order) {
        var type = switch (order.status()) {
            case Order.BACKORDERED -> CustomerNotification.Type.ORDER_BACKORDERED;
            case Order.PENDING -> CustomerNotification.Type.ORDER_PENDING;
            default -> CustomerNotification.Type.ORDER_APPROVED;
        };
        notifications.enqueue(order, type, order.createdAt());
    }

    @Override
    /** Finds the committed checkout result for a customer-owned idempotency key. */
    public Optional<Order> orderByIdempotencyKey(String customerId, String key) {
        return database.execute("orders.by_idempotency", true,
                () -> orders.findByCustomerIdAndIdempotencyKey(customerId, key).map(OrderJpaEntity::toDomain));
    }

    @Override
    /** Returns a customer's orders in reverse creation order. */
    public List<Order> orders(String customerId) {
        return database.execute("orders.by_customer", true, () -> orders
                .findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(OrderJpaEntity::toDomain).toList());
    }

    @Override @Transactional
    /** Seeds the initial catalog without overwriting existing product data. */
    public void seedIfEmpty() {
        database.execute("catalog.seed", false, () -> {
            for (var product : SeedProducts.all()) {
                var existing = products.findById(product.id());
                if (existing.isEmpty()) {
                    products.save(new ProductJpaEntity(product));
                } else {
                    existing.get().applyCatalogMetadata(product);
                    products.save(existing.get());
                }
            }
            products.flush();
        });
    }

    /** Rejects stale writes by comparing the expected and current optimistic-lock versions. */
    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new StoreConflictException("Expected cart version " + expected + " but found " + actual);
    }
}
