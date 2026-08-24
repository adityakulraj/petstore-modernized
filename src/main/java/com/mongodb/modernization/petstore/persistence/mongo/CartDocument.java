package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;

@Document("carts")
class CartDocument {
    @Id String id;
    @Indexed(unique = true) String customerId;
    @Version Long version;
    ArrayList<CartLineDocument> lines = new ArrayList<>();

    CartDocument() {}
    CartDocument(String customerId) { this.id = customerId; this.customerId = customerId; }
    Cart toDomain() {
        return new Cart(id, customerId, version == null ? 0 : version,
                lines.stream().map(CartLineDocument::toDomain).toList());
    }
    void replaceWith(Cart cart) {
        lines.clear(); cart.lines().stream().map(CartLineDocument::new).forEach(lines::add);
    }
}
