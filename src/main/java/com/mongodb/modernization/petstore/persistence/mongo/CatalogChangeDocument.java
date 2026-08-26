package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.IndexDirection;

import java.math.BigDecimal;
import java.time.Instant;

@Document("catalogChanges")
class CatalogChangeDocument {
    @Id String id;
    String productId;
    CatalogChange.Action action;
    String changedBy;
    @Indexed(direction = IndexDirection.DESCENDING) Instant occurredAt;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal previousPrice;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal newPrice;
    Boolean previousActive;
    Boolean newActive;
    Long previousVersion;
    long newVersion;

    CatalogChangeDocument() {}
    CatalogChangeDocument(CatalogChange change) {
        id = change.id(); productId = change.productId(); action = change.action(); changedBy = change.changedBy();
        occurredAt = change.occurredAt(); previousPrice = change.previousPrice(); newPrice = change.newPrice();
        previousActive = change.previousActive(); newActive = change.newActive();
        previousVersion = change.previousVersion(); newVersion = change.newVersion();
    }
    CatalogChange toDomain() {
        return new CatalogChange(id, productId, action, changedBy, occurredAt, previousPrice, newPrice,
                previousActive, newActive, previousVersion, newVersion);
    }
}
