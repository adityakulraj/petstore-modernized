package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;

@Entity
@Table(name = "PS_PRODUCT", indexes = @Index(name = "IX_PS_PRODUCT_CATEGORY", columnList = "CATEGORY_ID"))
class ProductJpaEntity {
    @Id @Column(length = 40) String id;
    @Column(name = "PRODUCT_GROUP_ID", length = 40) String productGroupId;
    @Column(name = "VARIANT_NAME", length = 80) String variantName;
    @Column(name = "CATEGORY_ID", nullable = false, length = 40) String categoryId;
    @Column(name = "CATEGORY_NAME", nullable = false, length = 80) String categoryName;
    @Column(nullable = false, length = 120) String name;
    @Column(nullable = false, length = 1000) String description;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal price;
    @Column(nullable = false) int stock;
    // Nullable only for rolling upgrades: pre-catalog-management rows have no value and are treated as active.
    @Column Boolean active = true;
    @Version long version;

    protected ProductJpaEntity() {}

    ProductJpaEntity(Product product) {
        id = product.id(); productGroupId = product.productGroupId(); variantName = product.variantName();
        categoryId = product.categoryId(); categoryName = product.categoryName();
        name = product.name(); description = product.description(); price = product.price(); stock = product.stock();
        active = product.active();
    }

    Product toDomain() { return new Product(id, productGroupId == null ? id : productGroupId,
            variantName == null ? "Standard" : variantName, categoryId, categoryName, name, description,
            price, stock, active == null || active, version); }

    void applyCatalogMetadata(Product product) {
        productGroupId = product.productGroupId(); variantName = product.variantName();
        categoryId = product.categoryId(); categoryName = product.categoryName();
        name = product.name(); description = product.description();
    }

    void applyCatalogUpdate(Product product) {
        applyCatalogMetadata(product);
        price = product.price();
        active = product.active();
    }

    void replaceStock(int quantity) { stock = quantity; }
    void reserveStock(int quantity) { stock -= quantity; }
}
