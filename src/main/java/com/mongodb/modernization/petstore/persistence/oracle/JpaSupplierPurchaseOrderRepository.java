package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface JpaSupplierPurchaseOrderRepository extends JpaRepository<SupplierPurchaseOrderJpaEntity, String> {
    Optional<SupplierPurchaseOrderJpaEntity> findByOrderId(String orderId);
    List<SupplierPurchaseOrderJpaEntity> findAllByOrderByCreatedAtDesc();
}
