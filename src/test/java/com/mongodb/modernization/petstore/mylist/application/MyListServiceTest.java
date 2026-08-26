package com.mongodb.modernization.petstore.mylist.application;

import com.mongodb.modernization.petstore.accounts.application.CustomerAccountService;
import com.mongodb.modernization.petstore.accounts.application.CustomerAccountStore;
import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.catalog.application.SeedProducts;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.mylist.domain.FavoriteItem;
import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import com.mongodb.modernization.petstore.shared.domain.Address;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MyListServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void recommendsSiblingVariantBeforeTheCustomersPreferredCategory() {
        var fixture = fixture(true, List.of(new FavoriteItem("alice", "K9-BD-01", NOW)));

        var view = fixture.service.myList("alice");

        assertThat(view.favorites()).extracting(product -> product.id()).containsExactly("K9-BD-01");
        assertThat(view.recommendations()).extracting(product -> product.id())
                .startsWith("K9-BD-02").contains("K9-RT-01").doesNotContain("K9-BD-01");
    }

    @Test
    void disablingMyListPausesRecommendationsWithoutDeletingSavedItems() {
        var fixture = fixture(false, List.of(new FavoriteItem("alice", "K9-BD-01", NOW)));

        var view = fixture.service.myList("alice");

        assertThat(view.enabled()).isFalse();
        assertThat(view.favorites()).extracting(product -> product.id()).containsExactly("K9-BD-01");
        assertThat(view.recommendations()).isEmpty();
    }

    private static Fixture fixture(boolean preference, List<FavoriteItem> favorites) {
        var catalog = SeedProducts.all();
        var address = new Address("Alice", "1 Main", "", "Pune", "MH", "411001", "India");
        var account = new CustomerAccount("alice", "hash", "Alice", "alice@example.test", "1", address,
                "en", "DOGS", preference, true);
        MyListStore lists = new FixedMyListStore(favorites);
        StorefrontStore storefront = new CatalogOnlyStore(catalog);
        CustomerAccountService accounts = new CustomerAccountService(new FixedAccountStore(account), new PlainEncoder());
        return new Fixture(new MyListService(lists, storefront, accounts,
                Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private record Fixture(MyListService service) {}

    private record FixedMyListStore(List<FavoriteItem> items) implements MyListStore {
        @Override public List<FavoriteItem> favorites(String customerId) { return items; }
        @Override public void add(String customerId, String itemId, Instant addedAt) { throw unsupported(); }
        @Override public void remove(String customerId, String itemId) { throw unsupported(); }
    }

    private record FixedAccountStore(CustomerAccount value) implements CustomerAccountStore {
        @Override public Optional<CustomerAccount> account(String username) { return Optional.of(value); }
        @Override public CustomerAccount save(CustomerAccount account) { throw unsupported(); }
    }

    private record CatalogOnlyStore(List<Product> catalog) implements StorefrontStore {
        @Override public List<Product> products(String categoryId) { return catalog; }
        @Override public Optional<Product> product(String productId) { return catalog.stream().filter(p -> p.id().equals(productId)).findFirst(); }
        @Override public Cart cart(String customerId) { throw unsupported(); }
        @Override public Cart addToCart(String customerId, long version, String productId, int quantity) { throw unsupported(); }
        @Override public Cart updateCart(String customerId, long version, String productId, int quantity) { throw unsupported(); }
        @Override public Cart removeFromCart(String customerId, long version, String productId) { throw unsupported(); }
        @Override public Order checkout(String customerId, long version, String key, Address address) { throw unsupported(); }
        @Override public Optional<Order> orderByIdempotencyKey(String customerId, String key) { throw unsupported(); }
        @Override public List<Order> orders(String customerId) { throw unsupported(); }
        @Override public void seedIfEmpty() { throw unsupported(); }
    }

    private static final class PlainEncoder implements PasswordEncoder {
        @Override public String encode(CharSequence rawPassword) { return rawPassword.toString(); }
        @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return rawPassword.toString().equals(encodedPassword);
        }
    }

    private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
}
