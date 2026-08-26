package com.mongodb.modernization.petstore.catalog.domain;

import java.math.BigDecimal;

public record Product(String id, String productGroupId, String variantName, String categoryId,
                      String categoryName, String name, String description, BigDecimal price,
                      int stock, boolean active, long version) {
    public Product(String id, String productGroupId, String variantName, String categoryId,
                   String categoryName, String name, String description, BigDecimal price,
                   int stock, long version) {
        this(id, productGroupId, variantName, categoryId, categoryName, name, description, price,
                stock, true, version);
    }

    public Product(String id, String categoryId, String categoryName, String name,
                   String description, BigDecimal price, int stock, long version) {
        this(id, id, "Standard", categoryId, categoryName, name, description, price, stock, true, version);
    }

    public String displayName() {
        return variantName == null || variantName.isBlank() || "Standard".equalsIgnoreCase(variantName)
                ? name : variantName + " " + name;
    }
}
