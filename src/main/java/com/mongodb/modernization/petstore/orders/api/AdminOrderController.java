package com.mongodb.modernization.petstore.orders.api;

import com.mongodb.modernization.petstore.orders.application.AdminOrderService;
import com.mongodb.modernization.petstore.orders.application.AdminOrderStore;
import com.mongodb.modernization.petstore.orders.domain.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final AdminOrderService orders;

    public AdminOrderController(AdminOrderService orders) { this.orders = orders; }

    @GetMapping
    public List<Order> orders() { return orders.orders(); }

    @PostMapping("/{orderId}/decision")
    public Order review(@PathVariable String orderId, @Valid @RequestBody ReviewRequest request,
                        Authentication authentication) {
        return orders.review(orderId, request.expectedVersion(), request.decision(), authentication.getName());
    }

    public record ReviewRequest(@Min(0) long expectedVersion, @NotNull AdminOrderStore.Decision decision) {}
}
