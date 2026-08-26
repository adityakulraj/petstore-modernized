package com.mongodb.modernization.petstore.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record CatalogChange(String id, String productId, Action action, String changedBy, Instant occurredAt,
                            BigDecimal previousPrice, BigDecimal newPrice,
                            Boolean previousActive, Boolean newActive,
                            Long previousVersion, long newVersion) {
    public enum Action { CREATED, UPDATED }
}
