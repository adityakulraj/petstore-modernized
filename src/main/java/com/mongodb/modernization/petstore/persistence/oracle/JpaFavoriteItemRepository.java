package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

interface JpaFavoriteItemRepository extends JpaRepository<FavoriteItemJpaEntity, String> {
    /** Queries persisted records by customer id order by added at desc. */
    List<FavoriteItemJpaEntity> findByCustomerIdOrderByAddedAtDesc(String customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = "MERGE INTO PS_FAVORITE_ITEM target " +
            "USING (SELECT :id ID, :customerId CUSTOMER_ID, :itemId ITEM_ID, :addedAt ADDED_AT FROM dual) source " +
            "ON (target.ID = source.ID) WHEN NOT MATCHED THEN " +
            "INSERT (ID, CUSTOMER_ID, ITEM_ID, ADDED_AT) " +
            "VALUES (source.ID, source.CUSTOMER_ID, source.ITEM_ID, source.ADDED_AT)", nativeQuery = true)
    /** Executes the add if absent persistence operation against the selected database. */
    int addIfAbsent(@Param("id") String id, @Param("customerId") String customerId,
                    @Param("itemId") String itemId, @Param("addedAt") Instant addedAt);
}
