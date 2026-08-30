package com.mongodb.modernization.petstore.supplier.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SupplierPageController {
    @GetMapping({"/supplier", "/supplier/"})
    /** Handles the supplier portal HTTP request and returns its API response. */
    public String supplierPortal() {
        return "forward:/supplier/index.html";
    }
}
