package com.mongodb.modernization.petstore.mylist.application;

import com.mongodb.modernization.petstore.mylist.domain.FavoriteItem;

import java.time.Instant;
import java.util.List;

public interface MyListStore {
    /** Executes the favorites persistence operation against the selected database. */
    List<FavoriteItem> favorites(String customerId);
    /** Executes the add persistence operation against the selected database. */
    void add(String customerId, String itemId, Instant addedAt);
    /** Executes the remove persistence operation against the selected database. */
    void remove(String customerId, String itemId);
}
