package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("supplierInventoryCommands")
class SupplierInventoryCommandDocument {
    @Id String id;
    @Indexed String productId;
    long expectedVersion;
    int quantity;
    int resultStock;
    long resultVersion;
    Instant completedAt;

    SupplierInventoryCommandDocument() {}

    SupplierInventoryCommandDocument(String id, String productId, long expectedVersion, int quantity,
                                     int resultStock, long resultVersion, Instant completedAt) {
        this.id = id;
        this.productId = productId;
        this.expectedVersion = expectedVersion;
        this.quantity = quantity;
        this.resultStock = resultStock;
        this.resultVersion = resultVersion;
        this.completedAt = completedAt;
    }

    boolean matches(String productId, long expectedVersion, int quantity) {
        return this.productId.equals(productId) && this.expectedVersion == expectedVersion && this.quantity == quantity;
    }
}
