package com.mongodb.modernization.petstore.orders.api;

import com.mongodb.modernization.petstore.orders.domain.Order;
import com.mongodb.modernization.petstore.shared.application.StorefrontService;
import com.mongodb.modernization.petstore.shared.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final StorefrontService storefront;

    public OrderController(StorefrontService storefront) { this.storefront = storefront; }

    @GetMapping
    public List<Order> orders(Authentication authentication) { return storefront.orders(authentication.getName()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Order checkout(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
                          @Valid @RequestBody CheckoutRequest request, Authentication authentication) {
        return storefront.checkout(authentication.getName(), request.expectedCartVersion(), idempotencyKey,
                request.address().toDomain());
    }

    public record CheckoutRequest(@Min(0) long expectedCartVersion, @NotNull @Valid AddressRequest address) {}

    public record AddressRequest(@NotBlank @Size(max = 100) String fullName,
                                 @NotBlank @Size(max = 150) String line1,
                                 @Size(max = 150) String line2,
                                 @NotBlank @Size(max = 80) String city,
                                 @NotBlank @Size(max = 80) String state,
                                 @NotBlank @Size(max = 20) String postalCode,
                                 @NotBlank @Size(max = 80) String country) {
        Address toDomain() { return new Address(fullName, line1, line2, city, state, postalCode, country); }
    }
}
