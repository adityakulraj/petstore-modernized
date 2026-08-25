package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface MongoProductRepository extends MongoRepository<ProductDocument, String> {
    List<ProductDocument> findByCategoryIdOrderByIdAsc(String categoryId);
}
