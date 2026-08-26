package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.mylist.domain.FavoriteItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("favoriteItems")
@CompoundIndexes(@CompoundIndex(name = "IX_FAVORITE_CUSTOMER_ADDED", def = "{'customerId': 1, 'addedAt': -1}"))
class FavoriteItemDocument {
    @Id String id;
    String customerId;
    String itemId;
    Instant addedAt;

    FavoriteItem toDomain() {
        return new FavoriteItem(customerId, itemId, addedAt);
    }
}
