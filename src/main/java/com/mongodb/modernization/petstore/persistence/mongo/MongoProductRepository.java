package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface MongoProductRepository extends MongoRepository<ProductDocument, String> {
    /** Queries persisted records by category id order by id asc. */
    List<ProductDocument> findByCategoryIdOrderByIdAsc(String categoryId);
}
