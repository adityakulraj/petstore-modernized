package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PS_CART")
class CartJpaEntity {
    @Id @Column(name = "CUSTOMER_ID", length = 100) String customerId;
    @Version long version;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "PS_CART_LINE", joinColumns = @JoinColumn(name = "CUSTOMER_ID"))
    @OrderColumn(name = "LINE_NUMBER")
    List<CartLineJpa> lines = new ArrayList<>();

    protected CartJpaEntity() {}
    CartJpaEntity(String customerId) { this.customerId = customerId; }

    Cart toDomain() { return new Cart(customerId, customerId, version, lines.stream().map(CartLineJpa::toDomain).toList()); }
    void replaceWith(Cart cart) {
        lines.clear();
        cart.lines().stream().map(CartLineJpa::new).forEach(lines::add);
    }
}
