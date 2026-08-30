package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface JpaProductRepository extends JpaRepository<ProductJpaEntity, String> {
    /** Queries persisted records by category id order by id asc. */
    List<ProductJpaEntity> findByCategoryIdOrderByIdAsc(String categoryId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductJpaEntity p where p.id = :id")
    /** Queries persisted records by id for update. */
    Optional<ProductJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductJpaEntity p set p.stock = p.stock - :quantity, p.version = p.version + 1 " +
            "where p.id = :id and p.stock >= :quantity")
    /** Executes the decrement stock persistence operation against the selected database. */
    int decrementStock(@Param("id") String id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductJpaEntity p set p.stock = p.stock + :quantity, p.version = p.version + 1 where p.id = :id")
    /** Executes the restore stock persistence operation against the selected database. */
    int restoreStock(@Param("id") String id, @Param("quantity") int quantity);
}
