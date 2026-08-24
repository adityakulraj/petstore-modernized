package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;

@Document("products")
class ProductDocument {
    @Id String id;
    @Indexed String categoryId;
    String categoryName;
    String name;
    String description;
    @Field(targetType = FieldType.DECIMAL128) BigDecimal price;
    int stock;
    @Version Long version;

    ProductDocument() {}
    ProductDocument(Product product) {
        id = product.id(); categoryId = product.categoryId(); categoryName = product.categoryName();
        name = product.name(); description = product.description(); price = product.price(); stock = product.stock();
    }
    Product toDomain() { return new Product(id, categoryId, categoryName, name, description, price, stock, version == null ? 0 : version); }
}
