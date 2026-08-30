package com.mongodb.modernization.petstore.config;

import com.mongodb.modernization.petstore.accounts.application.CustomerAccountStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {
    @Bean
    /** Provides the BCrypt encoder used to hash and verify local account passwords. */
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    /** Configures or applies users behavior for the application runtime. */
    UserDetailsService users(AppProperties properties, CustomerAccountStore accounts, PasswordEncoder encoder) {
        var admin = User.withUsername(properties.admin().username())
                .password(encoder.encode(properties.admin().password()))
                .roles("ADMIN")
                .build();
        var supplier = User.withUsername(properties.supplier().username())
                .password(encoder.encode(properties.supplier().password()))
                .roles("SUPPLIER")
                .build();
        return username -> {
            // Authentication erases credentials on the returned UserDetails. Return a fresh copy so
            // one successful login cannot erase the shared demo principal's password for later logins.
            if (admin.getUsername().equals(username)) return User.withUserDetails(admin).build();
            if (supplier.getUsername().equals(username)) return User.withUserDetails(supplier).build();
            return accounts.account(username)
                    .map(account -> User.withUsername(account.username()).password(account.passwordHash()).roles("CUSTOMER").build())
                    .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        };
    }

    /** Configures or applies authentication provider behavior for the application runtime. */
    DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    @Order(1)
    /** Configures or applies diagnostic security filter chain behavior for the application runtime. */
    SecurityFilterChain diagnosticSecurityFilterChain(HttpSecurity http, UserDetailsService users,
                                                      PasswordEncoder encoder) throws Exception {
        var sessionContexts = new HttpSessionSecurityContextRepository();
        var requestContexts = new RequestAttributeSecurityContextRepository();
        return http
                // Basic auth is intentionally limited to read-only diagnostic endpoints. Browsers cache
                // Basic credentials per origin and must never let those credentials replace a form-login
                // session used by customer, supplier, or administrator workflows.
                .securityMatcher("/api/v1/admin/logs", "/api/v1/admin/health")
                .authenticationProvider(authenticationProvider(users, encoder))
                // The HTML operations dashboard is opened through form login, so this chain must read that
                // existing admin session. Basic-auth identities are request-only: they can call diagnostics
                // from curl/health tooling but can never overwrite the browser's business-workflow session.
                .securityContext(context -> context.securityContextRepository(sessionContexts))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(basic -> basic.securityContextRepository(requestContexts))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                                "object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))
                .build();
    }

    @Bean
    @Order(2)
    /** Defines authentication, authorization, CSRF, login, and logout rules for HTTP requests. */
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users, PasswordEncoder encoder) throws Exception {
        var sessionContexts = new HttpSessionSecurityContextRepository();
        return http
                // Business workflows use a form-login session exclusively. This keeps one stable role
                // throughout a browser session, even when the browser has cached old Basic credentials.
                .authenticationProvider(authenticationProvider(users, encoder))
                .securityContext(context -> context.securityContextRepository(sessionContexts))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/api/v1/catalog/**", "/api/v1/session", "/api/v1/csrf",
                                "/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").permitAll()
                        .requestMatchers("/api/v1/accounts/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/my-list/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/v1/notifications/**").hasRole("CUSTOMER")
                        .requestMatchers("/supplier/**", "/api/v1/supplier/**").hasRole("SUPPLIER")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form.defaultSuccessUrl("/", false))
                .logout(logout -> logout.logoutSuccessUrl("/"))
                // Registration creates an unauthenticated principal, so it cannot be used to mutate an existing session.
                // Profile, cart, checkout, logout, and every other authenticated mutation still require a CSRF token.
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/accounts")))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                                "object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))
                .build();
    }
}
