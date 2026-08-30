package com.mongodb.modernization.petstore.catalog.application;

import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.math.BigDecimal;
import java.util.List;

public final class SeedProducts {
    /** Prevents instantiation of the static seed-catalog factory. */
    private SeedProducts() {}

    /** Returns the deterministic initial catalog shared by both database implementations. */
    public static List<Product> all() {
        return List.of(
                product("FI-SW-01", "FI-SW", "Standard", "FISH", "Fish", "Angelfish", "A peaceful freshwater aquarium favorite.", "16.50", 25),
                product("FI-SW-02", "FI-SH", "Standard", "FISH", "Fish", "Tiger Shark", "A striking fish for experienced keepers.", "21.50", 12),
                product("K9-BD-01", "K9-BD", "Male Adult", "DOGS", "Dogs", "Bulldog", "Confident, affectionate companion.", "850.00", 4),
                product("K9-BD-02", "K9-BD", "Female Puppy", "DOGS", "Dogs", "Bulldog", "Playful puppy with a gentle temperament.", "850.00", 4),
                product("K9-RT-01", "K9-RT", "Standard", "DOGS", "Dogs", "Golden Retriever", "Friendly family companion.", "950.00", 5),
                product("FL-DSH-01", "FL-DSH", "Standard", "CATS", "Cats", "Domestic Shorthair", "Playful and adaptable companion.", "320.00", 8),
                product("AV-CB-01", "AV-CB", "Standard", "BIRDS", "Birds", "Canary", "Bright songbird with a gentle temperament.", "125.00", 10),
                product("RP-IG-01", "RP-IG", "Standard", "REPTILES", "Reptiles", "Green Iguana", "Arboreal reptile requiring specialist care.", "180.00", 6)
        );
    }

    /** Creates a seed product from readable string values with an initial version of zero. */
    private static Product product(String id, String productGroupId, String variantName, String category,
                                   String categoryName, String name, String description, String price, int stock) {
        return new Product(id, productGroupId, variantName, category, categoryName, name, description,
                new BigDecimal(price), stock, 0);
    }
}
