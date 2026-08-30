package com.mongodb.modernization.petstore.persistence.oracle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface JpaPaymentRepository extends JpaRepository<PaymentJpaEntity, String> {
    /** Queries persisted records by customer id order by created at desc. */
    List<PaymentJpaEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.orderId = :orderId")
    /** Queries persisted records by order id for update. */
    Optional<PaymentJpaEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
