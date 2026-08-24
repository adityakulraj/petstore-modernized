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
    @Column(name = "CATEGORY_ID", nullable = false, length = 40) String categoryId;
    @Column(name = "CATEGORY_NAME", nullable = false, length = 80) String categoryName;
    @Column(nullable = false, length = 120) String name;
    @Column(nullable = false, length = 1000) String description;
    @Column(nullable = false, precision = 12, scale = 2) BigDecimal price;
    @Column(nullable = false) int stock;
    @Version long version;

    protected ProductJpaEntity() {}

    ProductJpaEntity(Product product) {
        id = product.id(); categoryId = product.categoryId(); categoryName = product.categoryName();
        name = product.name(); description = product.description(); price = product.price(); stock = product.stock();
    }

    Product toDomain() { return new Product(id, categoryId, categoryName, name, description, price, stock, version); }
}
