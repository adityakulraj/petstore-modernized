package com.mongodb.modernization.petstore.mylist.application;

import com.mongodb.modernization.petstore.mylist.domain.FavoriteItem;

import java.time.Instant;
import java.util.List;

public interface MyListStore {
    List<FavoriteItem> favorites(String customerId);
    void add(String customerId, String itemId, Instant addedAt);
    void remove(String customerId, String itemId);
}
