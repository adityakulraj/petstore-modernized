package com.mongodb.modernization.petstore.persistence.oracle;

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
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

@Repository
@Profile("oracle")
class OracleStorefrontStore implements StorefrontStore {
    private final JpaProductRepository products;
    private final JpaCartRepository carts;
    private final JpaOrderRepository orders;
    private final Clock clock = Clock.systemUTC();

    OracleStorefrontStore(JpaProductRepository products, JpaCartRepository carts, JpaOrderRepository orders) {
        this.products = products; this.carts = carts; this.orders = orders;
    }

    @Override @Transactional(readOnly = true)
    public List<Product> products() {
        return products.findAll().stream().map(ProductJpaEntity::toDomain).sorted(Comparator.comparing(Product::id)).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<Product> product(String productId) { return products.findById(productId).map(ProductJpaEntity::toDomain); }

    @Override @Transactional
    public Cart cart(String customerId) {
        return carts.findById(customerId).orElseGet(() -> carts.saveAndFlush(new CartJpaEntity(customerId))).toDomain();
    }

    @Override @Transactional
    public Cart addToCart(String customerId, long expectedVersion, String productId, int quantity) {
        var product = products.findById(productId).map(ProductJpaEntity::toDomain)
                .orElseThrow(() -> new NotFoundException("Unknown product " + productId));
        return mutateCart(customerId, expectedVersion, cart -> cart.add(product, quantity));
    }

    @Override @Transactional
    public Cart updateCart(String customerId, long expectedVersion, String productId, int quantity) {
        return mutateCart(customerId, expectedVersion, cart -> cart.update(productId, quantity));
    }

    @Override @Transactional
    public Cart removeFromCart(String customerId, long expectedVersion, String productId) {
        return mutateCart(customerId, expectedVersion, cart -> cart.remove(productId));
    }

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

    @Override @Transactional
    public Order checkout(String customerId, long expectedCartVersion, String key, Address address) {
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, key);
        if (existing.isPresent()) return existing.get().toDomain();
        var cartEntity = carts.findById(customerId).orElseThrow(() -> new StoreConflictException("Cart is empty"));
        var cart = cartEntity.toDomain();
        requireVersion(cart.version(), expectedCartVersion);
        if (cart.lines().isEmpty()) throw new StoreConflictException("Cart is empty");
        for (var line : cart.lines()) {
            if (products.decrementStock(line.productId(), line.quantity()) != 1) {
                throw new InsufficientStockException(line.productId());
            }
        }
        var order = Order.placed(UUID.randomUUID().toString(), customerId, key, Instant.now(clock), address, cart);
        try {
            orders.saveAndFlush(new OrderJpaEntity(order));
            cartEntity.replaceWith(Cart.empty(cart.id(), customerId, cart.version()));
            carts.saveAndFlush(cartEntity);
            return order;
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateCheckoutException(duplicate);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new StoreConflictException("The cart changed during checkout; refresh and retry", conflict);
        }
    }

    @Override @Transactional(readOnly = true)
    public Optional<Order> orderByIdempotencyKey(String customerId, String key) {
        return orders.findByCustomerIdAndIdempotencyKey(customerId, key).map(OrderJpaEntity::toDomain);
    }

    @Override @Transactional(readOnly = true)
    public List<Order> orders(String customerId) {
        return orders.findByCustomerIdOrderByCreatedAtDesc(customerId).stream().map(OrderJpaEntity::toDomain).toList();
    }

    @Override @Transactional
    public void seedIfEmpty() {
        if (products.count() > 0) return;
        products.saveAll(SeedProducts.all().stream().map(ProductJpaEntity::new).toList());
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new StoreConflictException("Expected cart version " + expected + " but found " + actual);
    }
}
