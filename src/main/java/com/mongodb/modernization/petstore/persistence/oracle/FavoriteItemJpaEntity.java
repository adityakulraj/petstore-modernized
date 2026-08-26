package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.mylist.domain.FavoriteItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "PS_FAVORITE_ITEM",
        indexes = @Index(name = "IX_PS_FAVORITE_CUSTOMER", columnList = "CUSTOMER_ID,ADDED_AT"),
        uniqueConstraints = @UniqueConstraint(name = "UK_PS_FAVORITE_CUSTOMER_ITEM",
                columnNames = {"CUSTOMER_ID", "ITEM_ID"}))
class FavoriteItemJpaEntity {
    @Id @Column(length = 101) String id;
    @Column(name = "CUSTOMER_ID", nullable = false, length = 50) String customerId;
    @Column(name = "ITEM_ID", nullable = false, length = 40) String itemId;
    @Column(name = "ADDED_AT", nullable = false) Instant addedAt;

    protected FavoriteItemJpaEntity() {}

    FavoriteItemJpaEntity(String customerId, String itemId, Instant addedAt) {
        this.id = customerId + ":" + itemId;
        this.customerId = customerId;
        this.itemId = itemId;
        this.addedAt = addedAt;
    }

    FavoriteItem toDomain() {
        return new FavoriteItem(customerId, itemId, addedAt);
    }
}
