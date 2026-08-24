package com.mongodb.modernization.petstore.catalog.application;

import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.math.BigDecimal;
import java.util.List;

public final class SeedProducts {
    private SeedProducts() {}

    public static List<Product> all() {
        return List.of(
                product("FI-SW-01", "FISH", "Fish", "Angelfish", "A peaceful freshwater aquarium favorite.", "16.50", 25),
                product("FI-SW-02", "FISH", "Fish", "Tiger Shark", "A striking fish for experienced keepers.", "21.50", 12),
                product("K9-BD-01", "DOGS", "Dogs", "Bulldog", "Confident, affectionate companion.", "850.00", 4),
                product("K9-RT-01", "DOGS", "Dogs", "Golden Retriever", "Friendly family companion.", "950.00", 5),
                product("FL-DSH-01", "CATS", "Cats", "Domestic Shorthair", "Playful and adaptable companion.", "320.00", 8),
                product("AV-CB-01", "BIRDS", "Birds", "Canary", "Bright songbird with a gentle temperament.", "125.00", 10),
                product("RP-IG-01", "REPTILES", "Reptiles", "Green Iguana", "Arboreal reptile requiring specialist care.", "180.00", 6)
        );
    }

    private static Product product(String id, String category, String categoryName, String name,
                                   String description, String price, int stock) {
        return new Product(id, category, categoryName, name, description, new BigDecimal(price), stock, 0);
    }
}
