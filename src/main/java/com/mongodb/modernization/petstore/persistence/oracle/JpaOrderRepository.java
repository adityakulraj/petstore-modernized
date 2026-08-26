package com.mongodb.modernization.petstore.persistence.oracle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface JpaOrderRepository extends JpaRepository<OrderJpaEntity, String> {
    Optional<OrderJpaEntity> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    List<OrderJpaEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc();

    List<OrderJpaEntity> findAllByStatusOrderByCreatedAtAsc(String status);
    List<OrderJpaEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Instant fromInclusive, Instant toExclusive);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderJpaEntity o where o.status = :status order by o.createdAt asc")
    List<OrderJpaEntity> findByStatusForUpdate(@Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderJpaEntity o where o.id = :id")
    Optional<OrderJpaEntity> findByIdForReview(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderJpaEntity o set o.status = :status, o.version = o.version + 1 " +
            "where o.id = :id and o.status = 'APPROVED'")
    int completeApproved(@Param("id") String id, @Param("status") String status);
}
