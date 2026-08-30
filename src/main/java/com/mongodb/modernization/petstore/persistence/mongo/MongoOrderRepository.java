package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface MongoOrderRepository extends MongoRepository<OrderDocument, String> {
    /** Queries persisted records by customer id and idempotency key. */
    Optional<OrderDocument> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);
    /** Queries persisted records by customer id order by created at desc. */
    List<OrderDocument> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    /** Executes the find all by order by created at desc persistence operation against the selected database. */
    List<OrderDocument> findAllByOrderByCreatedAtDesc();
    /** Queries persisted records by status order by created at asc. */
    List<OrderDocument> findByStatusOrderByCreatedAtAsc(String status);
    @Query(value = "{ 'createdAt': { '$gte': ?0, '$lt': ?1 } }", sort = "{ 'createdAt': 1 }")
    /** Executes the find for sales analytics persistence operation against the selected database. */
    List<OrderDocument> findForSalesAnalytics(Instant fromInclusive, Instant toExclusive);
}
