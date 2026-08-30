package com.mongodb.modernization.petstore.catalog.application;

import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import com.mongodb.modernization.petstore.catalog.domain.Product;

import java.util.List;
import java.util.Optional;

public interface CatalogStore {
    /** Executes the products persistence operation against the selected database. */
    List<Product> products();
    /** Executes the product persistence operation against the selected database. */
    Optional<Product> product(String id);
    /** Creates . */
    Product create(Product product, String changedBy);
    /** Executes the update persistence operation against the selected database. */
    Product update(Product target, long expectedVersion, String changedBy);
    /** Executes the changes persistence operation against the selected database. */
    List<CatalogChange> changes();
}
