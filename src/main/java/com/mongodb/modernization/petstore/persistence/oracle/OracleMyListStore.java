package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.mylist.application.MyListStore;
import com.mongodb.modernization.petstore.mylist.domain.FavoriteItem;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@Profile("oracle")
class OracleMyListStore implements MyListStore {
    private final JpaFavoriteItemRepository favorites;
    private final DatabaseExecutor database;

    /** Creates the Oracle MyList adapter with its repositories and database executor. */
    OracleMyListStore(JpaFavoriteItemRepository favorites, DatabaseExecutor database) {
        this.favorites = favorites;
        this.database = database;
    }

    @Override
    /** Executes the favorites persistence operation against the selected database. */
    public List<FavoriteItem> favorites(String customerId) {
        return database.execute("my_list.items.all", true,
                () -> favorites.findByCustomerIdOrderByAddedAtDesc(customerId).stream()
                        .map(FavoriteItemJpaEntity::toDomain).toList());
    }

    @Override
    /** Executes the add persistence operation against the selected database. */
    public void add(String customerId, String itemId, Instant addedAt) {
        var id = customerId + ":" + itemId;
        try {
            database.execute("my_list.item.add", true,
                    () -> favorites.addIfAbsent(id, customerId, itemId, addedAt));
        } catch (DataIntegrityViolationException duplicateRace) {
            if (!favorites.existsById(id)) throw duplicateRace;
        }
    }

    @Override
    /** Executes the remove persistence operation against the selected database. */
    public void remove(String customerId, String itemId) {
        database.execute("my_list.item.remove", true, () -> favorites.deleteById(customerId + ":" + itemId));
    }
}
