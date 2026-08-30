package com.mongodb.modernization.petstore.persistence.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

interface MongoCustomerOrderCommandRepository extends MongoRepository<CustomerOrderCommandDocument, String> {}
