package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface MongoPaymentRepository extends MongoRepository<PaymentDocument, String> {
    /** Queries persisted records by customer id order by created at desc. */
    List<PaymentDocument> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
