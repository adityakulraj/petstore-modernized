package com.mongodb.modernization.petstore.persistence.oracle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "PS_SUPPLIER_INV_COMMAND",
        indexes = @Index(name = "IX_PS_SUPPLIER_INV_CMD_PRODUCT", columnList = "PRODUCT_ID"))
class SupplierInventoryCommandJpaEntity {
    @Id @Column(length = 100) String id;
    @Column(name = "PRODUCT_ID", nullable = false, length = 40) String productId;
    @Column(name = "EXPECTED_VERSION", nullable = false) long expectedVersion;
    @Column(nullable = false) int quantity;
    @Column(name = "RESULT_STOCK", nullable = false) int resultStock;
    @Column(name = "RESULT_VERSION", nullable = false) long resultVersion;
    @Column(name = "COMPLETED_AT", nullable = false) Instant completedAt;

    protected SupplierInventoryCommandJpaEntity() {}

    SupplierInventoryCommandJpaEntity(String id, String productId, long expectedVersion, int quantity,
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
