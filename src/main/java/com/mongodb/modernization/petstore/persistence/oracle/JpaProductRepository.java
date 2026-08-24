package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaProductRepository extends JpaRepository<ProductJpaEntity, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductJpaEntity p set p.stock = p.stock - :quantity, p.version = p.version + 1 " +
            "where p.id = :id and p.stock >= :quantity")
    int decrementStock(@Param("id") String id, @Param("quantity") int quantity);
}
