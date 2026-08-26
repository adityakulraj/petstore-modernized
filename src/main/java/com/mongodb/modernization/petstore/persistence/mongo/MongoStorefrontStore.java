package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.catalog.application.SeedProducts;
import com.mongodb.modernization.petstore.config.AppProperties;
import com.mongodb.modernization.petstore.orders.application.DuplicateCheckoutException;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.notifications.application.CustomerNotificationStore;
import com.mongodb.modernization.petstore.notifications.domain.CustomerNotification;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
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
@Profile("mongo")
class MongoStorefrontStore implements StorefrontStore {
    private final MongoProductRepository products;
    private final MongoCartRepository carts;
    private final MongoOrderRepository orders;
    private final MongoTemplate template;
    private final CustomerNotificationStore notifications;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final BigDecimal approvalThreshold;
    private final Clock clock = Clock.systemUTC();

    MongoStorefrontStore(MongoProductRepository products, MongoCartRepository carts,
                         MongoOrderRepository orders, MongoTemplate template,
                         @Qualifier("mongoTransactionManager") PlatformTransactionManager transactionManager,
                         DatabaseExecutor database, AppProperties properties,
                         CustomerNotificationStore notifications) {
        this.products = products; this.carts = carts; this.orders = orders; this.template = template;
        this.notifications = notifications;
        this.transactions = new TransactionTemplate(transactionManager);
        this.database = database;
        this.approvalThreshold = properties.admin().approvalThreshold();
    }

    @Override public List<Product> products(String categoryId) {
        boolean filtered = categoryId != null && !categoryId.isBlank();
        return database.execute(filtered ? "catalog.products.by_category" : "catalog.products.all", true,
                () -> (filtered ? products.findByCategoryIdOrderByIdAsc(categoryId.trim().toUpperCase(Locale.ROOT))
                        : products.findAll()).stream()
                .map(ProductDocument::toDomain).filter(Product::active)
                .sorted(Comparator.comparing(Product::id)).toList());
    }
    @Override public Optional<Product> product(String productId) {
        return database.execute("catalog.product.by_id", true,
                () -> products.findById(productId).map(ProductDocument::toDomain).filter(Product::active));
    }

    @Override
    public Cart cart(String customerId) {
        return database.execute("cart.by_customer", true, () -> transactions.execute(ignored -> {
            try {
                return carts.findById(customerId).orElseGet(() -> carts.save(new CartDocument(customerId))).toDomain();
            } catch (DataIntegrityViolationException race) {
                return carts.findById(customerId).orElseThrow(() -> race).toDomain();
            }
        }));
    }

    @Override @Transactional("mongoTransactionManager")
    public Cart addToCart(String customerId, long expectedVersion, String productId, int quantity) {
        return database.execute("cart.add", false, () -> {
            var product = products.findById(productId).map(ProductDocument::toDomain)
                    .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
            return mutateCart(customerId, expectedVersion, cart -> cart.add(product, quantity));
        });
    }
    @Override @Transactional("mongoTransactionManager")
    public Cart updateCart(String customerId, long expectedVersion, String productId, int quantity) {
        return database.execute("cart.update", false,
                () -> mutateCart(customerId, expectedVersion, cart -> cart.update(productId, quantity)));
    }
    @Override @Transactional("mongoTransactionManager")
    public Cart removeFromCart(String customerId, long expectedVersion, String productId) {
        return database.execute("cart.remove", false,
                () -> mutateCart(customerId, expectedVersion, cart -> cart.remove(productId)));
    }

    private Cart mutateCart(String customerId, long expectedVersion, UnaryOperator<Cart> mutation) {
        try {
            var document = carts.findById(customerId).orElseGet(() -> new CartDocument(customerId));
            var current = document.toDomain();
            requireVersion(current.version(), expectedVersion);
            document.replaceWith(mutation.apply(current));
            return carts.save(document).toDomain();
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException conflict) {
            throw new StoreConflictException("The cart changed in another request; refresh and retry", conflict);
        }
    }

    @Override
    public Order checkout(String customerId, long expectedCartVersion, String key, Address address) {
        // Checkout is safe to replay because its key is unique and the whole operation is transactional.
        return database.execute("orders.checkout", true,
                () -> transactions.execute(status -> checkoutOnce(customerId, expectedCartVersion, key, address)));
    }

    private Order checkoutOnce(String customerId, long expectedCartVersion, String key, Address address) {
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
        if (existing.isPresent()) {
            var replay = existing.get().toDomain();
            enqueueCheckoutNotification(replay);
            return replay;
        }
        var cartDocument = carts.findById(customerId).orElseThrow(() -> new StoreConflictException("Cart is empty"));
        var cart = cartDocument.toDomain();
        requireVersion(cart.version(), expectedCartVersion);
        if (cart.lines().isEmpty()) throw new StoreConflictException("Cart is empty");
        var inventoryAvailable = cart.lines().stream().allMatch(line -> products.findById(line.productId())
                .map(product -> product.stock >= line.quantity()).orElse(false));
        if (inventoryAvailable) {
            for (var line : cart.lines()) {
                // A competing transaction produces a write conflict and the retry re-evaluates availability.
                var query = Query.query(Criteria.where("_id").is(line.productId()).and("stock").gte(line.quantity()));
                var result = template.updateFirst(query,
                        new Update().inc("stock", -line.quantity()).inc("version", 1), ProductDocument.class);
                if (result.getModifiedCount() != 1) {
                    throw new OptimisticLockingFailureException("Inventory changed during checkout");
                }
            }
        }
        var now = Instant.now(clock);
        var order = inventoryAvailable
                ? Order.submitted(UUID.randomUUID().toString(), customerId, key, now, address, cart, approvalThreshold)
                : Order.backordered(UUID.randomUUID().toString(), customerId, key, now, address, cart);
        try {
            orders.insert(new OrderDocument(order));
            enqueueCheckoutNotification(order);
            cartDocument.replaceWith(Cart.empty(cart.id(), customerId, cart.version()));
            carts.save(cartDocument);
            return order;
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateCheckoutException(duplicate);
        } catch (OptimisticLockingFailureException conflict) {
            throw new StoreConflictException("The cart changed during checkout; refresh and retry", conflict);
        }
    }

    private void enqueueCheckoutNotification(Order order) {
        var type = switch (order.status()) {
            case Order.BACKORDERED -> CustomerNotification.Type.ORDER_BACKORDERED;
            case Order.PENDING -> CustomerNotification.Type.ORDER_PENDING;
            default -> CustomerNotification.Type.ORDER_APPROVED;
        };
        notifications.enqueue(order, type, order.createdAt());
    }

    @Override public Optional<Order> orderByIdempotencyKey(String customerId, String key) {
        return database.execute("orders.by_idempotency", true,
                () -> orders.findByCustomerIdAndIdempotencyKey(customerId, key).map(OrderDocument::toDomain));
    }
    @Override public List<Order> orders(String customerId) {
        return database.execute("orders.by_customer", true, () -> orders
                .findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(OrderDocument::toDomain).toList());
    }
    @Override public void seedIfEmpty() {
        database.execute("catalog.seed", false, () -> {
            for (var product : SeedProducts.all()) {
                var update = new Update()
                        .set("productGroupId", product.productGroupId()).set("variantName", product.variantName())
                        .set("categoryId", product.categoryId()).set("categoryName", product.categoryName())
                        .set("name", product.name()).set("description", product.description())
                        .setOnInsert("price", product.price()).setOnInsert("stock", product.stock())
                        .setOnInsert("active", true)
                        .setOnInsert("version", 0L);
                template.upsert(Query.query(Criteria.where("_id").is(product.id())), update, ProductDocument.class);
            }
        });
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new StoreConflictException("Expected cart version " + expected + " but found " + actual);
    }
}
