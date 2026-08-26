package com.mongodb.modernization.petstore.orders.application;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.supplier.application.SupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminOrderService {
    private static final Logger LOG = LoggerFactory.getLogger(AdminOrderService.class);
    private final AdminOrderStore store;
    private final SupplierService supplier;

    public AdminOrderService(AdminOrderStore store, SupplierService supplier) {
        this.store = store;
        this.supplier = supplier;
    }

    public List<Order> orders() { return store.orders(); }

    public Order review(String orderId, long expectedVersion, AdminOrderStore.Decision decision, String reviewer) {
        var order = store.review(orderId, expectedVersion, decision, reviewer);
        if (decision == AdminOrderStore.Decision.APPROVED) supplier.ensurePurchaseOrder(order);
        LOG.atInfo()
                .addKeyValue("event", decision == AdminOrderStore.Decision.APPROVED
                        ? "admin.order.approved" : "admin.order.denied")
                .addKeyValue("orderId", order.id())
                .addKeyValue("customerId", order.customerId())
                .addKeyValue("orderVersion", order.version())
                .addKeyValue("reviewer", reviewer)
                .addKeyValue("total", order.total())
                .log("Administrator order review completed");
        return order;
    }
}
