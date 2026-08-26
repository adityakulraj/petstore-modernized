package com.mongodb.modernization.petstore.shared.api;

import com.mongodb.modernization.petstore.config.AppProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SessionController {
    private final AppProperties properties;

    public SessionController(AppProperties properties) { this.properties = properties; }

    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication) {
        var admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        var supplier = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_SUPPLIER"));
        return Map.of("authenticated", authentication != null, "username",
                authentication == null ? "" : authentication.getName(),
                "store", properties.store().name().toLowerCase(), "admin", admin, "supplier", supplier);
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
