package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PS_SUPPLIER_PO", indexes = @Index(name = "IX_PS_SUPPLIER_PO_CREATED", columnList = "CREATED_AT"))
class SupplierPurchaseOrderJpaEntity {
    @Id @Column(length = 36) String id;
    @Column(name = "ORDER_ID", nullable = false, unique = true, length = 36) String orderId;
    @Column(name = "CUSTOMER_ID", nullable = false, length = 100) String customerId;
    @Column(name = "CREATED_AT", nullable = false) Instant createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) SupplierPurchaseOrder.Status status;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "PS_SUPPLIER_PO_LINE", joinColumns = @JoinColumn(name = "SUPPLIER_PO_ID"),
            indexes = @Index(name = "IX_PS_SUPPLIER_PO_LINE", columnList = "SUPPLIER_PO_ID"))
    @OrderColumn(name = "LINE_NUMBER")
    List<OrderLineJpa> lines = new ArrayList<>();
    @Version long version;
    @Column(name = "PROCESSED_AT") Instant processedAt;

    /** Creates a supplier purchase order jpa entity and wires its required collaborators. */
    protected SupplierPurchaseOrderJpaEntity() {}

    /** Creates a supplier purchase order jpa entity and wires its required collaborators. */
    SupplierPurchaseOrderJpaEntity(SupplierPurchaseOrder purchaseOrder) {
        id = purchaseOrder.id();
        orderId = purchaseOrder.orderId();
        customerId = purchaseOrder.customerId();
        createdAt = purchaseOrder.createdAt();
        status = purchaseOrder.status();
        purchaseOrder.lines().stream().map(OrderLineJpa::new).forEach(lines::add);
        processedAt = purchaseOrder.processedAt();
    }

    /** Provides the persistence mapping behavior for mark processed. */
    void markProcessed(Instant when) {
        status = SupplierPurchaseOrder.Status.PROCESSED;
        processedAt = when;
    }

    /** Provides the persistence mapping behavior for cancel. */
    void cancel() { status = SupplierPurchaseOrder.Status.CANCELLED; }

    /** Maps this persistence representation to the corresponding domain model. */
    SupplierPurchaseOrder toDomain() {
        return new SupplierPurchaseOrder(id, orderId, customerId, createdAt, status,
                lines.stream().map(OrderLineJpa::toDomain).toList(), version, processedAt);
    }
}
