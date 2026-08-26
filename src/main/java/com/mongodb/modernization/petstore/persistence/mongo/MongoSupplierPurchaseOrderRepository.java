package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface MongoSupplierPurchaseOrderRepository extends MongoRepository<SupplierPurchaseOrderDocument, String> {
    Optional<SupplierPurchaseOrderDocument> findByOrderId(String orderId);
    List<SupplierPurchaseOrderDocument> findAllByOrderByCreatedAtDesc();
}
