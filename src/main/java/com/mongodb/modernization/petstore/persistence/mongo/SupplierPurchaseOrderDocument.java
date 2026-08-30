package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;

@Document("supplierPurchaseOrders")
class SupplierPurchaseOrderDocument {
    @Id String id;
    @Indexed(unique = true) String orderId;
    String customerId;
    Instant createdAt;
    SupplierPurchaseOrder.Status status;
    ArrayList<OrderLineDocument> lines = new ArrayList<>();
    @Version Long version;
    Instant processedAt;

    SupplierPurchaseOrderDocument() {}

    SupplierPurchaseOrderDocument(SupplierPurchaseOrder purchaseOrder) {
        id = purchaseOrder.id();
        orderId = purchaseOrder.orderId();
        customerId = purchaseOrder.customerId();
        createdAt = purchaseOrder.createdAt();
        status = purchaseOrder.status();
        purchaseOrder.lines().stream().map(OrderLineDocument::new).forEach(lines::add);
        processedAt = purchaseOrder.processedAt();
    }

    void markProcessed(Instant when) {
        status = SupplierPurchaseOrder.Status.PROCESSED;
        processedAt = when;
    }

    void cancel() { status = SupplierPurchaseOrder.Status.CANCELLED; }

    SupplierPurchaseOrder toDomain() {
        return new SupplierPurchaseOrder(id, orderId, customerId, createdAt, status,
                lines.stream().map(OrderLineDocument::toDomain).toList(), version == null ? 0 : version, processedAt);
    }
}
