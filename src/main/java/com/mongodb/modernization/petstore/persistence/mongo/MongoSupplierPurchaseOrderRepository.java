package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface MongoSupplierPurchaseOrderRepository extends MongoRepository<SupplierPurchaseOrderDocument, String> {
    /** Queries persisted records by order id. */
    Optional<SupplierPurchaseOrderDocument> findByOrderId(String orderId);
    /** Executes the find all by order by created at desc persistence operation against the selected database. */
    List<SupplierPurchaseOrderDocument> findAllByOrderByCreatedAtDesc();
}
