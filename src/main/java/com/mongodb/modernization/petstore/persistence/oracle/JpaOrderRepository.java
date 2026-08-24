package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface JpaOrderRepository extends JpaRepository<OrderJpaEntity, String> {
    Optional<OrderJpaEntity> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    List<OrderJpaEntity> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
