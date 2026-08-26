package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface MongoOrderRepository extends MongoRepository<OrderDocument, String> {
    Optional<OrderDocument> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    List<OrderDocument> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<OrderDocument> findAllByOrderByCreatedAtDesc();
}
