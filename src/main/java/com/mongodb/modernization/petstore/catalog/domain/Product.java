package com.mongodb.modernization.petstore.catalog.domain;

import java.math.BigDecimal;

/** Independently priced and stocked catalog item, optionally grouped with sibling variants. */
public record Product(String id, String productGroupId, String variantName, String categoryId,
                      String categoryName, String name, String description, BigDecimal price,
                      int stock, boolean active, long version) {
    /** Creates an active variant-aware product for callers that do not supply publication state. */
    public Product(String id, String productGroupId, String variantName, String categoryId,
                   String categoryName, String name, String description, BigDecimal price,
                   int stock, long version) {
        this(id, productGroupId, variantName, categoryId, categoryName, name, description, price,
                stock, true, version);
    }

    /** Creates an active standard-variant product for legacy-compatible callers. */
    public Product(String id, String categoryId, String categoryName, String name,
                   String description, BigDecimal price, int stock, long version) {
        this(id, id, "Standard", categoryId, categoryName, name, description, price, stock, true, version);
    }

    /** Returns the customer-facing name with a non-standard variant prefix when present. */
    public String displayName() {
        return variantName == null || variantName.isBlank() || "Standard".equalsIgnoreCase(variantName)
                ? name : variantName + " " + name;
    }
}
