package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "PS_CATALOG_CHANGE", indexes = @Index(name = "IX_PS_CATALOG_CHANGE_TIME", columnList = "OCCURRED_AT"))
class CatalogChangeJpaEntity {
    @Id @Column(length = 40) String id;
    @Column(name = "PRODUCT_ID", nullable = false, length = 40) String productId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) CatalogChange.Action action;
    @Column(name = "CHANGED_BY", nullable = false, length = 80) String changedBy;
    @Column(name = "OCCURRED_AT", nullable = false) Instant occurredAt;
    @Column(name = "PREVIOUS_PRICE", precision = 12, scale = 2) BigDecimal previousPrice;
    @Column(name = "NEW_PRICE", nullable = false, precision = 12, scale = 2) BigDecimal newPrice;
    @Column(name = "PREVIOUS_ACTIVE") Boolean previousActive;
    @Column(name = "NEW_ACTIVE", nullable = false) Boolean newActive;
    @Column(name = "PREVIOUS_VERSION") Long previousVersion;
    @Column(name = "NEW_VERSION", nullable = false) long newVersion;

    /** Creates a catalog change jpa entity and wires its required collaborators. */
    protected CatalogChangeJpaEntity() {}
    /** Creates a catalog change jpa entity and wires its required collaborators. */
    CatalogChangeJpaEntity(CatalogChange change) {
        id = change.id(); productId = change.productId(); action = change.action(); changedBy = change.changedBy();
        occurredAt = change.occurredAt(); previousPrice = change.previousPrice(); newPrice = change.newPrice();
        previousActive = change.previousActive(); newActive = change.newActive();
        previousVersion = change.previousVersion(); newVersion = change.newVersion();
    }
    /** Maps this persistence representation to the corresponding domain model. */
    CatalogChange toDomain() {
        return new CatalogChange(id, productId, action, changedBy, occurredAt, previousPrice, newPrice,
                previousActive, newActive, previousVersion, newVersion);
    }
}
