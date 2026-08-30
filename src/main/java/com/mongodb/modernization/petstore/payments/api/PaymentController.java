package com.mongodb.modernization.petstore.payments.api;

import com.mongodb.modernization.petstore.payments.domain.Payment;
import com.mongodb.modernization.petstore.shared.application.StorefrontService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final StorefrontService storefront;

    public PaymentController(StorefrontService storefront) { this.storefront = storefront; }

    @GetMapping
    public List<Payment> payments(Authentication authentication) {
        return storefront.payments(authentication.getName());
    }
}
