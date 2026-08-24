package com.mongodb.modernization.petstore.catalog.domain;

import java.math.BigDecimal;

public record Product(String id, String categoryId, String categoryName, String name,
                      String description, BigDecimal price, int stock, long version) {
}
