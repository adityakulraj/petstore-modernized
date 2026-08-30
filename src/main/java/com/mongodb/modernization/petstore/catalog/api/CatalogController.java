package com.mongodb.modernization.petstore.catalog.api;

import com.mongodb.modernization.petstore.catalog.domain.Product;
import com.mongodb.modernization.petstore.shared.application.StorefrontService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final StorefrontService storefront;

    /** Creates a catalog controller and wires its required collaborators. */
    public CatalogController(StorefrontService storefront) { this.storefront = storefront; }

    @GetMapping("/products")
    /** Handles the products HTTP request and returns its API response. */
    public List<Product> products(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String query) {
        // Category filtering is database-backed so the category index is used; free-text contains preserves legacy semantics.
        var products = storefront.products(category).stream();
        if (query != null && !query.isBlank()) {
            var needle = query.toLowerCase(Locale.ROOT).trim();
            products = products.filter(product -> (product.variantName() + " " + product.name() + " " + product.description())
                    .toLowerCase(Locale.ROOT).contains(needle));
        }
        return products.toList();
    }

    @GetMapping("/products/{id}")
    /** Handles the product HTTP request and returns its API response. */
    public Product product(@PathVariable String id) { return storefront.product(id); }

    @GetMapping("/categories")
    /** Handles the categories HTTP request and returns its API response. */
    public List<CategoryView> categories() {
        return storefront.products().stream()
                .map(product -> new CategoryView(product.categoryId(), product.categoryName()))
                .distinct().toList();
    }

    /** Handles the category view HTTP request and returns its API response. */
    public record CategoryView(String id, String name) {}
}
