package com.mongodb.modernization.petstore.mylist.application;

import com.mongodb.modernization.petstore.accounts.application.CustomerAccountService;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StorefrontStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class MyListService {
    private static final Logger LOG = LoggerFactory.getLogger(MyListService.class);
    private static final int RECOMMENDATION_LIMIT = 4;
    private final MyListStore lists;
    private final StorefrontStore storefront;
    private final CustomerAccountService accounts;
    private final Clock clock;

    @Autowired
    public MyListService(MyListStore lists, StorefrontStore storefront, CustomerAccountService accounts) {
        this(lists, storefront, accounts, Clock.systemUTC());
    }

    MyListService(MyListStore lists, StorefrontStore storefront, CustomerAccountService accounts, Clock clock) {
        this.lists = lists;
        this.storefront = storefront;
        this.accounts = accounts;
        this.clock = clock;
    }

    public MyListView myList(String customerId) {
        var account = accounts.account(customerId);
        var catalog = storefront.products();
        var byId = catalog.stream().collect(Collectors.toMap(Product::id, product -> product));
        var favorites = lists.favorites(customerId).stream().map(item -> byId.get(item.itemId()))
                .filter(java.util.Objects::nonNull).toList();
        var recommendations = account.myListPreference()
                ? recommendations(catalog, favorites, account.favoriteCategory()) : List.<Product>of();
        return new MyListView(account.myListPreference(), favorites, recommendations);
    }

    public MyListView add(String customerId, String itemId) {
        requireItem(itemId);
        lists.add(customerId, itemId, Instant.now(clock));
        LOG.atInfo().addKeyValue("event", "my_list.item.added").addKeyValue("customerId", customerId)
                .addKeyValue("itemId", itemId).log("Customer added an item to MyList");
        return myList(customerId);
    }

    public MyListView remove(String customerId, String itemId) {
        lists.remove(customerId, itemId);
        LOG.atInfo().addKeyValue("event", "my_list.item.removed").addKeyValue("customerId", customerId)
                .addKeyValue("itemId", itemId).log("Customer removed an item from MyList");
        return myList(customerId);
    }

    private void requireItem(String itemId) {
        storefront.product(itemId).orElseThrow(() -> new NotFoundException("Unknown product " + itemId));
    }

    private static List<Product> recommendations(List<Product> catalog, List<Product> favorites,
                                                 String favoriteCategory) {
        var favoriteIds = favorites.stream().map(Product::id).collect(Collectors.toSet());
        var favoriteGroups = favorites.stream().map(Product::productGroupId).collect(Collectors.toSet());
        var favoriteCategories = favorites.stream().map(Product::categoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var selected = new LinkedHashMap<String, Product>();
        addMatching(selected, catalog, product -> favoriteGroups.contains(product.productGroupId()), favoriteIds);
        var preferred = favoriteCategory == null ? "" : favoriteCategory.trim().toUpperCase(Locale.ROOT);
        addMatching(selected, catalog, product -> product.categoryId().equals(preferred), favoriteIds);
        addMatching(selected, catalog, product -> favoriteCategories.contains(product.categoryId()), favoriteIds);
        return selected.values().stream().limit(RECOMMENDATION_LIMIT).toList();
    }

    private static void addMatching(LinkedHashMap<String, Product> selected, List<Product> catalog,
                                    Predicate<Product> predicate, java.util.Set<String> excluded) {
        catalog.stream().filter(product -> !excluded.contains(product.id())).filter(predicate)
                .forEach(product -> selected.putIfAbsent(product.id(), product));
    }

    public record MyListView(boolean enabled, List<Product> favorites, List<Product> recommendations) {}
}
