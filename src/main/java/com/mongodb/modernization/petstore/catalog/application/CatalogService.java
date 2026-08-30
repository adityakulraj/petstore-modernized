package com.mongodb.modernization.petstore.catalog.application;

import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class CatalogService {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogService.class);
    private final CatalogStore store;

    /** Creates a catalog service and wires its required collaborators. */
    public CatalogService(CatalogStore store) { this.store = store; }

    /** Coordinates the products application use case. */
    public List<Product> products() { return store.products(); }
    /** Coordinates the changes application use case. */
    public List<CatalogChange> changes() { return store.changes(); }

    /** Creates . */
    public Product create(String id, String productGroupId, String variantName, String categoryId,
                          String categoryName, String name, String description, BigDecimal price,
                          boolean active, String changedBy) {
        var requested = new Product(code(id), code(productGroupId), text(variantName), code(categoryId),
                text(categoryName), text(name), text(description), price, 0, active, 0);
        var created = store.create(requested, changedBy);
        log("admin.catalog.created", created, changedBy);
        return created;
    }

    /** Coordinates the update application use case. */
    public Product update(String id, long expectedVersion, String productGroupId, String variantName,
                          String categoryId, String categoryName, String name, String description,
                          BigDecimal price, boolean active, String changedBy) {
        var current = store.product(code(id)).orElseThrow(() -> new NotFoundException("Unknown product " + id));
        var target = new Product(current.id(), code(productGroupId), text(variantName), code(categoryId),
                text(categoryName), text(name), text(description), price, current.stock(), active, current.version());
        var updated = store.update(target, expectedVersion, changedBy);
        log("admin.catalog.updated", updated, changedBy);
        return updated;
    }

    /** Coordinates the code application use case. */
    private static String code(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    /** Coordinates the text application use case. */
    private static String text(String value) { return value.trim(); }

    /** Writes structured telemetry for . */
    private static void log(String event, Product product, String changedBy) {
        LOG.atInfo().addKeyValue("event", event).addKeyValue("productId", product.id())
                .addKeyValue("price", product.price()).addKeyValue("active", product.active())
                .addKeyValue("catalogVersion", product.version()).addKeyValue("changedBy", changedBy)
                .log("Catalog administration completed");
    }
}
