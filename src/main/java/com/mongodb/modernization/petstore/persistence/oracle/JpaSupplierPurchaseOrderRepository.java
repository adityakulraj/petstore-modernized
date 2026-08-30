package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

interface JpaSupplierPurchaseOrderRepository extends JpaRepository<SupplierPurchaseOrderJpaEntity, String> {
    Optional<SupplierPurchaseOrderJpaEntity> findByOrderId(String orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SupplierPurchaseOrderJpaEntity p where p.orderId = :orderId")
    Optional<SupplierPurchaseOrderJpaEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SupplierPurchaseOrderJpaEntity p where p.id = :id")
    Optional<SupplierPurchaseOrderJpaEntity> findByIdForUpdate(@Param("id") String id);
    List<SupplierPurchaseOrderJpaEntity> findAllByOrderByCreatedAtDesc();
}
