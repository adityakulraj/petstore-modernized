package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface MongoOrderRepository extends MongoRepository<OrderDocument, String> {
    Optional<OrderDocument> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    List<OrderDocument> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<OrderDocument> findAllByOrderByCreatedAtDesc();
    List<OrderDocument> findByStatusOrderByCreatedAtAsc(String status);
    @Query(value = "{ 'createdAt': { '$gte': ?0, '$lt': ?1 } }", sort = "{ 'createdAt': 1 }")
    List<OrderDocument> findForSalesAnalytics(Instant fromInclusive, Instant toExclusive);
}
