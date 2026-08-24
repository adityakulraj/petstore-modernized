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

    public CatalogController(StorefrontService storefront) { this.storefront = storefront; }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) String query) {
        var products = storefront.products().stream();
        if (category != null && !category.isBlank()) {
            products = products.filter(product -> product.categoryId().equalsIgnoreCase(category));
        }
        if (query != null && !query.isBlank()) {
            var needle = query.toLowerCase(Locale.ROOT).trim();
            products = products.filter(product -> (product.name() + " " + product.description())
                    .toLowerCase(Locale.ROOT).contains(needle));
        }
        return products.toList();
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable String id) { return storefront.product(id); }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return storefront.products().stream()
                .map(product -> new CategoryView(product.categoryId(), product.categoryName()))
                .distinct().toList();
    }

    public record CategoryView(String id, String name) {}
}
