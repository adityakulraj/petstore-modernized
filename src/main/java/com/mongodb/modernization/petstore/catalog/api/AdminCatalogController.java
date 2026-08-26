package com.mongodb.modernization.petstore.catalog.api;

import com.mongodb.modernization.petstore.catalog.application.CatalogService;
import com.mongodb.modernization.petstore.catalog.domain.CatalogChange;
import com.mongodb.modernization.petstore.catalog.domain.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog")
public class AdminCatalogController {
    private final CatalogService catalog;

    public AdminCatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/items")
    public List<Product> items() { return catalog.products(); }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody CreateItem request, Principal principal) {
        return catalog.create(request.id(), request.productGroupId(), request.variantName(), request.categoryId(),
                request.categoryName(), request.name(), request.description(), request.price(), request.active(),
                principal.getName());
    }

    @PutMapping("/items/{id}")
    public Product update(@PathVariable String id, @Valid @RequestBody UpdateItem request, Principal principal) {
        return catalog.update(id, request.expectedVersion(), request.productGroupId(), request.variantName(),
                request.categoryId(), request.categoryName(), request.name(), request.description(), request.price(),
                request.active(), principal.getName());
    }

    @GetMapping("/changes")
    public List<CatalogChange> changes() { return catalog.changes(); }

    public record CreateItem(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{2,39}") String id,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{1,39}") String productGroupId,
            @NotBlank @Size(max = 80) String variantName,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{1,39}") String categoryId,
            @NotBlank @Size(max = 80) String categoryName,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal price,
            boolean active) {}

    public record UpdateItem(
            @Min(0) long expectedVersion,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{1,39}") String productGroupId,
            @NotBlank @Size(max = 80) String variantName,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9-]{1,39}") String categoryId,
            @NotBlank @Size(max = 80) String categoryName,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("999999.99") @Digits(integer = 6, fraction = 2) BigDecimal price,
            boolean active) {}
}
