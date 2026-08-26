package com.mongodb.modernization.petstore.orders.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {
    @GetMapping("/admin/orders")
    public String orderApprovals() {
        return "forward:/admin/orders.html";
    }

    @GetMapping("/admin/sales")
    public String salesAnalytics() {
        return "forward:/admin/sales.html";
    }

    @GetMapping("/admin/catalog")
    public String catalogManagement() {
        return "forward:/admin/catalog.html";
    }
}
