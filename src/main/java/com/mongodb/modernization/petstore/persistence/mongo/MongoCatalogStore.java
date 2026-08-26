package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.catalog.application.CatalogStore;
import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.observability.DatabaseExecutor;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import com.mongodb.modernization.petstore.shared.application.StoreConflictException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("mongo")
class MongoCatalogStore implements CatalogStore {
    private final MongoProductRepository products;
    private final MongoCatalogChangeRepository changes;
    private final MongoTemplate template;
    private final TransactionTemplate transactions;
    private final DatabaseExecutor database;
    private final Clock clock = Clock.systemUTC();

    MongoCatalogStore(MongoProductRepository products, MongoCatalogChangeRepository changes, MongoTemplate template,
                      @Qualifier("mongoTransactionManager") PlatformTransactionManager transactionManager,
                      DatabaseExecutor database) {
        this.products = products; this.changes = changes; this.template = template;
        this.transactions = new TransactionTemplate(transactionManager); this.database = database;
    }

    @Override public List<Product> products() {
        return database.execute("admin.catalog.products.all", true, () -> products.findAll().stream()
                .map(ProductDocument::toDomain).sorted(Comparator.comparing(Product::id)).toList());
    }

    @Override public Optional<Product> product(String id) {
        return database.execute("admin.catalog.product.by_id", true,
                () -> products.findById(id).map(ProductDocument::toDomain));
    }

    @Override public Product create(Product requested, String changedBy) {
        try {
            return database.execute("admin.catalog.create", false, () -> transactions.execute(ignored -> {
                var existing = products.findById(requested.id()).map(ProductDocument::toDomain);
                if (existing.isPresent()) return createReplay(existing.get(), requested);
                var created = products.insert(new ProductDocument(requested)).toDomain();
                changes.insert(new CatalogChangeDocument(change(CatalogChange.Action.CREATED, null, created, changedBy)));
                return created;
            }));
        } catch (DataIntegrityViolationException race) {
            return database.execute("admin.catalog.create.replay", true, () -> products.findById(requested.id())
                    .map(ProductDocument::toDomain).map(existing -> createReplay(existing, requested))
                    .orElseThrow(() -> race));
        }
    }

    @Override public Product update(Product target, long expectedVersion, String changedBy) {
        return database.execute("admin.catalog.update", false, () -> transactions.execute(ignored -> {
            var before = products.findById(target.id()).map(ProductDocument::toDomain)
                    .orElseThrow(() -> new NotFoundException("Unknown product " + target.id()));
            if (sameCatalog(before, target)) return before;
            if (before.version() != expectedVersion) {
                throw new StoreConflictException("Catalog item changed in another request; refresh and retry");
            }
            var result = template.updateFirst(Query.query(Criteria.where("_id").is(target.id()).and("version").is(expectedVersion)),
                    new Update().set("productGroupId", target.productGroupId()).set("variantName", target.variantName())
                            .set("categoryId", target.categoryId()).set("categoryName", target.categoryName())
                            .set("name", target.name()).set("description", target.description())
                            .set("price", target.price()).set("active", target.active()).inc("version", 1),
                    ProductDocument.class);
            if (result.getModifiedCount() != 1) {
                throw new StoreConflictException("Catalog item changed in another request; refresh and retry");
            }
            var updated = products.findById(target.id()).orElseThrow().toDomain();
            changes.insert(new CatalogChangeDocument(change(CatalogChange.Action.UPDATED, before, updated, changedBy)));
            return updated;
        }));
    }

    @Override public List<CatalogChange> changes() {
        return database.execute("admin.catalog.changes.all", true, () -> changes.findAllByOrderByOccurredAtDesc()
                .stream().map(CatalogChangeDocument::toDomain).toList());
    }

    private Product createReplay(Product existing, Product requested) {
        if (!sameCatalog(existing, requested)) {
            throw new StoreConflictException("Product ID already exists with different catalog details");
        }
        return existing;
    }

    private CatalogChange change(CatalogChange.Action action, Product before, Product after, String changedBy) {
        return new CatalogChange(UUID.randomUUID().toString(), after.id(), action, changedBy, Instant.now(clock),
                before == null ? null : before.price(), after.price(), before == null ? null : before.active(),
                after.active(), before == null ? null : before.version(), after.version());
    }

    private static boolean sameCatalog(Product left, Product right) {
        return left.productGroupId().equals(right.productGroupId()) && left.variantName().equals(right.variantName())
                && left.categoryId().equals(right.categoryId()) && left.categoryName().equals(right.categoryName())
                && left.name().equals(right.name()) && left.description().equals(right.description())
                && left.price().compareTo(right.price()) == 0 && left.active() == right.active();
    }
}
