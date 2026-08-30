package com.mongodb.modernization.petstore.orders.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping("/admin/orders")
    /** Handles the order approvals HTTP request and returns its API response. */
    public String orderApprovals() {
        return "forward:/admin/orders.html";
    }

    @GetMapping("/admin/sales")
    /** Handles the sales analytics HTTP request and returns its API response. */
    public String salesAnalytics() {
        return "forward:/admin/sales.html";
    }

    @GetMapping("/admin/catalog")
    /** Handles the catalog management HTTP request and returns its API response. */
    public String catalogManagement() {
        return "forward:/admin/catalog.html";
    }
}
