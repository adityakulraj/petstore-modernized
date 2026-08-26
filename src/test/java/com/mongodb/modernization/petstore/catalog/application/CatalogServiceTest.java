package com.mongodb.modernization.petstore.catalog.application;

import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.shared.application.NotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogServiceTest {
    @Test
    void normalizesCodesAndLeavesInventorySupplierOwned() {
        var store = new RecordingStore();
        var service = new CatalogService(store);

        var created = service.create(" k9-ct-01 ", " k9-ct ", " Female Adult ", " cats ", " Cats ",
                " Siamese ", " Curious companion ", new BigDecimal("425.00"), true, "admin");

        assertThat(created.id()).isEqualTo("K9-CT-01");
        assertThat(created.productGroupId()).isEqualTo("K9-CT");
        assertThat(created.categoryId()).isEqualTo("CATS");
        assertThat(created.stock()).isZero();
        assertThat(store.changedBy).isEqualTo("admin");
    }

    @Test
    void updatesCatalogFieldsWhilePreservingSupplierStock() {
        var store = new RecordingStore();
        store.saved = new Product("K9-CT-01", "K9-CT", "Female Adult", "CATS", "Cats", "Siamese",
                "Curious companion", new BigDecimal("425.00"), 7, true, 4);
        var service = new CatalogService(store);

        var updated = service.update("k9-ct-01", 4, "k9-ct", "Female Adult", "cats", "Cats", "Siamese",
                "Calm companion", new BigDecimal("450.00"), false, "admin");

        assertThat(updated.stock()).isEqualTo(7);
        assertThat(updated.price()).isEqualByComparingTo("450.00");
        assertThat(updated.active()).isFalse();
        assertThat(store.expectedVersion).isEqualTo(4);
    }

    @Test
    void rejectsUnknownItemUpdates() {
        var service = new CatalogService(new RecordingStore());
        assertThatThrownBy(() -> service.update("missing", 0, "group", "Standard", "cats", "Cats", "Cat",
                "Description", new BigDecimal("10.00"), true, "admin"))
                .isInstanceOf(NotFoundException.class);
    }

    private static final class RecordingStore implements CatalogStore {
        Product saved;
        String changedBy;
        long expectedVersion;
        @Override public List<Product> products() { return saved == null ? List.of() : List.of(saved); }
        @Override public Optional<Product> product(String id) { return Optional.ofNullable(saved); }
        @Override public Product create(Product product, String actor) { saved = product; changedBy = actor; return product; }
        @Override public Product update(Product target, long version, String actor) {
            expectedVersion = version; changedBy = actor;
            saved = new Product(target.id(), target.productGroupId(), target.variantName(), target.categoryId(),
                    target.categoryName(), target.name(), target.description(), target.price(), target.stock(),
                    target.active(), version + 1);
            return saved;
        }
        @Override public List<CatalogChange> changes() { return new ArrayList<>(); }
    }
}
