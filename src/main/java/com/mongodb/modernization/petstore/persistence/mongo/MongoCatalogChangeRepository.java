package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

interface MongoCatalogChangeRepository extends MongoRepository<CatalogChangeDocument, String> {
    /** Executes the find all by order by occurred at desc persistence operation against the selected database. */
    List<CatalogChangeDocument> findAllByOrderByOccurredAtDesc();
}
