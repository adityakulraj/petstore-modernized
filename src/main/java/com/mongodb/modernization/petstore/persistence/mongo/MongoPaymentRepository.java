package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface MongoPaymentRepository extends MongoRepository<PaymentDocument, String> {
    List<PaymentDocument> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
