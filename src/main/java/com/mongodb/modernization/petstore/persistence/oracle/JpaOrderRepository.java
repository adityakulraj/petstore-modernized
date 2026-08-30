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
    /** Queries persisted records by customer id and idempotency key. */
    Optional<OrderJpaEntity> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    /** Queries persisted records by customer id order by created at desc. */
    List<OrderJpaEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    /** Executes the find all by order by created at desc persistence operation against the selected database. */
    List<OrderJpaEntity> findAllByOrderByCreatedAtDesc();

    /** Executes the find all by status order by created at asc persistence operation against the selected database. */
    List<OrderJpaEntity> findAllByStatusOrderByCreatedAtAsc(String status);
    /** Queries persisted records by created at greater than equal and created at less than order by created at asc. */
    List<OrderJpaEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
            Instant fromInclusive, Instant toExclusive);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderJpaEntity o where o.status = :status order by o.createdAt asc")
    /** Queries persisted records by status for update. */
    List<OrderJpaEntity> findByStatusForUpdate(@Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderJpaEntity o where o.id = :id")
    /** Queries persisted records by id for review. */
    Optional<OrderJpaEntity> findByIdForReview(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderJpaEntity o set o.status = :status, o.version = o.version + 1 " +
            "where o.id = :id and o.status = 'APPROVED'")
    /** Executes the complete approved persistence operation against the selected database. */
    int completeApproved(@Param("id") String id, @Param("status") String status);
}
