package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.MongoException;
import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.catalog.application.SeedProducts;
import com.mongodb.modernization.petstore.orders.application.DuplicateCheckoutException;
import com.mongodb.modernization.petstore.orders.application.InsufficientStockException;
import com.mongodb.modernization.petstore.orders.domain.Order;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.UnaryOperator;

@Repository
@Profile("mongo")
class MongoStorefrontStore implements StorefrontStore {
    private static final Logger LOG = LoggerFactory.getLogger(MongoStorefrontStore.class);
    private static final int MAX_TRANSACTION_ATTEMPTS = 5;
    private final MongoProductRepository products;
    private final MongoCartRepository carts;
    private final MongoOrderRepository orders;
    private final MongoTemplate template;
    private final TransactionTemplate transactions;
    private final Clock clock = Clock.systemUTC();

    MongoStorefrontStore(MongoProductRepository products, MongoCartRepository carts,
                         MongoOrderRepository orders, MongoTemplate template,
                         @Qualifier("mongoTransactionManager") PlatformTransactionManager transactionManager) {
        this.products = products; this.carts = carts; this.orders = orders; this.template = template;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override public List<Product> products() {
        return products.findAll().stream().map(ProductDocument::toDomain).sorted(Comparator.comparing(Product::id)).toList();
    }
    @Override public Optional<Product> product(String productId) { return products.findById(productId).map(ProductDocument::toDomain); }

    @Override @Transactional("mongoTransactionManager")
    public Cart cart(String customerId) {
        try {
            return carts.findById(customerId).orElseGet(() -> carts.save(new CartDocument(customerId))).toDomain();
        } catch (DataIntegrityViolationException race) {
            return carts.findById(customerId).orElseThrow(() -> race).toDomain();
        }
    }

    @Override @Transactional("mongoTransactionManager")
    public Cart addToCart(String customerId, long expectedVersion, String productId, int quantity) {
        var product = products.findById(productId).map(ProductDocument::toDomain)
                .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
        return mutateCart(customerId, expectedVersion, cart -> cart.add(product, quantity));
    }
    @Override @Transactional("mongoTransactionManager")
    public Cart updateCart(String customerId, long expectedVersion, String productId, int quantity) {
        return mutateCart(customerId, expectedVersion, cart -> cart.update(productId, quantity));
    }
    @Override @Transactional("mongoTransactionManager")
    public Cart removeFromCart(String customerId, long expectedVersion, String productId) {
        return mutateCart(customerId, expectedVersion, cart -> cart.remove(productId));
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
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.execute(status -> checkoutOnce(customerId, expectedCartVersion, key, address));
            } catch (RuntimeException failure) {
                if (attempt == MAX_TRANSACTION_ATTEMPTS || !isRetryableTransactionError(failure)) throw failure;
                LOG.atWarn()
                        .addKeyValue("event", "mongo.transaction.retry")
                        .addKeyValue("customerId", customerId)
                        .addKeyValue("attempt", attempt)
                        .log("Retrying transient checkout transaction");
                backoff(attempt);
            }
        }
    }

    private Order checkoutOnce(String customerId, long expectedCartVersion, String key, Address address) {
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
        if (existing.isPresent()) return existing.get().toDomain();
        var cartDocument = carts.findById(customerId).orElseThrow(() -> new StoreConflictException("Cart is empty"));
        var cart = cartDocument.toDomain();
        requireVersion(cart.version(), expectedCartVersion);
        if (cart.lines().isEmpty()) throw new StoreConflictException("Cart is empty");
        for (var line : cart.lines()) {
            // The conditional update is the inventory compare-and-set; the surrounding transaction rolls all lines back.
            var query = Query.query(Criteria.where("_id").is(line.productId()).and("stock").gte(line.quantity()));
            var result = template.updateFirst(query, new Update().inc("stock", -line.quantity()).inc("version", 1), ProductDocument.class);
            if (result.getModifiedCount() != 1) throw new InsufficientStockException(line.productId());
        }
        var order = Order.placed(UUID.randomUUID().toString(), customerId, key, Instant.now(clock), address, cart);
        try {
            orders.insert(new OrderDocument(order));
            cartDocument.replaceWith(Cart.empty(cart.id(), customerId, cart.version()));
            carts.save(cartDocument);
            return order;
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateCheckoutException(duplicate);
        } catch (OptimisticLockingFailureException conflict) {
            throw new StoreConflictException("The cart changed during checkout; refresh and retry", conflict);
        }
    }

    private static boolean isRetryableTransactionError(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo &&
                    (mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
                            || mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL))) {
                return true;
            }
        }
        return false;
    }

    private static void backoff(int attempt) {
        long milliseconds = ThreadLocalRandom.current().nextLong(5, 16) * attempt;
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(milliseconds));
        if (Thread.currentThread().isInterrupted()) {
            throw new StoreConflictException("Checkout retry interrupted");
        }
    }

    @Override public Optional<Order> orderByIdempotencyKey(String customerId, String key) {
        return orders.findByCustomerIdAndIdempotencyKey(customerId, key).map(OrderDocument::toDomain);
    }
    @Override public List<Order> orders(String customerId) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(OrderDocument::toDomain).toList();
    }
    @Override public void seedIfEmpty() {
        if (products.count() == 0) products.saveAll(SeedProducts.all().stream().map(ProductDocument::new).toList());
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new StoreConflictException("Expected cart version " + expected + " but found " + actual);
    }
}
