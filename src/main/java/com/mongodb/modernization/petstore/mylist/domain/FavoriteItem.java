package com.mongodb.modernization.petstore.mylist.domain;

import java.time.Instant;

/** Customer-owned saved item used by MyList and recommendation generation. */
public record FavoriteItem(String customerId, String itemId, Instant addedAt) {
    /** Builds the deterministic customer-and-item key used for replay-safe persistence. */
    public String id() {
        return customerId + ":" + itemId;
    }
}
