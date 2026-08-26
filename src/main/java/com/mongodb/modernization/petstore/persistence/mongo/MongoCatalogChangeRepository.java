package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface MongoCatalogChangeRepository extends MongoRepository<CatalogChangeDocument, String> {
    List<CatalogChangeDocument> findAllByOrderByOccurredAtDesc();
}
