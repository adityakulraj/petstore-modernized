package com.mongodb.modernization.petstore.persistence.oracle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface JpaCartRepository extends JpaRepository<CartJpaEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CartJpaEntity c where c.customerId = :customerId")
    /** Queries persisted records by id for checkout. */
    Optional<CartJpaEntity> findByIdForCheckout(String customerId);
}
