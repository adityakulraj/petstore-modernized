package com.mongodb.modernization.petstore.catalog.application;

import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.util.List;
import java.util.Optional;

public interface CatalogStore {
    List<Product> products();
    Optional<Product> product(String id);
    Product create(Product product, String changedBy);
    Product update(Product target, long expectedVersion, String changedBy);
    List<CatalogChange> changes();
}
