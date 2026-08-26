package com.mongodb.modernization.petstore.mylist.domain;

import java.time.Instant;

public record FavoriteItem(String customerId, String itemId, Instant addedAt) {
    public String id() {
        return customerId + ":" + itemId;
    }
}
