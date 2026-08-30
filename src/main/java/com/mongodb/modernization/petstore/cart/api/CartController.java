package com.mongodb.modernization.petstore.cart.api;

import com.mongodb.modernization.petstore.cart.domain.Cart;
import com.mongodb.modernization.petstore.shared.application.StorefrontService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final StorefrontService storefront;

    /** Creates a cart controller and wires its required collaborators. */
    public CartController(StorefrontService storefront) { this.storefront = storefront; }

    @GetMapping
    /** Handles the cart HTTP request and returns its API response. */
    public CartView cart(Authentication authentication) { return CartView.from(storefront.cart(authentication.getName())); }

    @PostMapping("/items")
    /** Handles the add HTTP request and returns its API response. */
    public CartView add(@Valid @RequestBody AddItemRequest request, Authentication authentication) {
        return CartView.from(storefront.add(authentication.getName(), request.expectedVersion(), request.productId(), request.quantity()));
    }

    @PutMapping("/items/{productId}")
    /** Handles the update HTTP request and returns its API response. */
    public CartView update(@PathVariable String productId, @Valid @RequestBody UpdateItemRequest request,
                       Authentication authentication) {
        return CartView.from(storefront.update(authentication.getName(), request.expectedVersion(), productId, request.quantity()));
    }

    @DeleteMapping("/items/{productId}")
    /** Handles the remove HTTP request and returns its API response. */
    public CartView remove(@PathVariable String productId, @RequestParam @Min(0) long expectedVersion,
                       Authentication authentication) {
        return CartView.from(storefront.remove(authentication.getName(), expectedVersion, productId));
    }

    /** Handles the add item request HTTP request and returns its API response. */
    public record AddItemRequest(@NotBlank String productId, @Min(1) @Max(99) int quantity,
                                 @Min(0) long expectedVersion) {}
    /** Handles the update item request HTTP request and returns its API response. */
    public record UpdateItemRequest(@Min(1) @Max(99) int quantity, @Min(0) long expectedVersion) {}
    /** Handles the cart view HTTP request and returns its API response. */
    public record CartView(String id, String customerId, long version,
                           java.util.List<com.mongodb.modernization.petstore.cart.domain.CartLine> lines,
                           BigDecimal total) {
        /** Handles the from HTTP request and returns its API response. */
        static CartView from(Cart cart) {
            return new CartView(cart.id(), cart.customerId(), cart.version(), cart.lines(), cart.total());
        }
    }
}
