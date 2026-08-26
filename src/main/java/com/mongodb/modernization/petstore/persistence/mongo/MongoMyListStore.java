package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.mylist.application.MyListStore;
import com.mongodb.modernization.petstore.mylist.domain.FavoriteItem;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("mongo")
class MongoMyListStore implements MyListStore {
    private final MongoTemplate template;
    private final DatabaseExecutor database;

    MongoMyListStore(MongoTemplate template, DatabaseExecutor database) {
        this.template = template;
        this.database = database;
    }

    @Override
    public List<FavoriteItem> favorites(String customerId) {
        return database.execute("my_list.items.all", true, () -> template.find(
                        Query.query(Criteria.where("customerId").is(customerId))
                                .with(Sort.by(Sort.Direction.DESC, "addedAt")), FavoriteItemDocument.class).stream()
                .map(FavoriteItemDocument::toDomain).toList());
    }

    @Override
    public void add(String customerId, String itemId, Instant addedAt) {
        database.execute("my_list.item.add", true, () -> template.upsert(
                Query.query(Criteria.where("_id").is(id(customerId, itemId))), new Update()
                        .setOnInsert("customerId", customerId).setOnInsert("itemId", itemId)
                        .setOnInsert("addedAt", addedAt), FavoriteItemDocument.class));
    }

    @Override
    public void remove(String customerId, String itemId) {
        database.execute("my_list.item.remove", true, () -> template.remove(
                Query.query(new Criteria().andOperator(Criteria.where("_id").is(id(customerId, itemId)),
                        Criteria.where("customerId").is(customerId))), FavoriteItemDocument.class));
    }

    private static String id(String customerId, String itemId) {
        return customerId + ":" + itemId;
    }
}
