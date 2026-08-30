package com.mongodb.modernization.petstore.supplier.api;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.supplier.application.SupplierService;
import com.mongodb.modernization.petstore.supplier.domain.SupplierPurchaseOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/supplier")
public class SupplierController {
    private final SupplierService supplier;

    /** Creates a supplier controller and wires its required collaborators. */
    public SupplierController(SupplierService supplier) { this.supplier = supplier; }

    @GetMapping("/inventory")
    /** Handles the inventory HTTP request and returns its API response. */
    public List<Product> inventory() { return supplier.inventory(); }

    @PutMapping("/inventory/{productId}")
    /** Handles the replace inventory HTTP request and returns its API response. */
    public Product replaceInventory(@PathVariable String productId,
                                    @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
                                    @Valid @RequestBody InventoryRequest request) {
        return supplier.replaceInventory(productId, request.expectedVersion(), request.quantity(), idempotencyKey);
    }

    @GetMapping("/backorders")
    /** Handles the backorders HTTP request and returns its API response. */
    public List<com.mongodb.modernization.petstore.orders.domain.Order> backorders() { return supplier.backorders(); }

    @GetMapping("/purchase-orders")
    /** Handles the purchase orders HTTP request and returns its API response. */
    public List<SupplierPurchaseOrder> purchaseOrders() { return supplier.purchaseOrders(); }

    @PostMapping("/purchase-orders/{purchaseOrderId}/process")
    /** Handles the process HTTP request and returns its API response. */
    public SupplierPurchaseOrder process(@PathVariable String purchaseOrderId,
                                         @Valid @RequestBody ProcessRequest request) {
        return supplier.processPurchaseOrder(purchaseOrderId, request.expectedVersion());
    }

    /** Handles the inventory request HTTP request and returns its API response. */
    public record InventoryRequest(@Min(0) long expectedVersion, @Min(0) @Max(1_000_000) int quantity) {}
    /** Handles the process request HTTP request and returns its API response. */
    public record ProcessRequest(@Min(0) long expectedVersion) {}
}
