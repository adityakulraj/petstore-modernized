package com.mongodb.modernization.petstore.config;

import com.mongodb.modernization.petstore.accounts.application.CustomerAccountStore;
import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
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

    DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService users, PasswordEncoder encoder) throws Exception {
        return http
                // Register one provider on this chain so form login and Basic auth share the same
                // dynamic customer lookup and the same admin/supplier principals.
                .authenticationProvider(authenticationProvider(users, encoder))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/api/v1/catalog/**", "/api/v1/session", "/api/v1/csrf",
                                "/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").permitAll()
                        .requestMatchers("/api/v1/accounts/**").hasRole("CUSTOMER")
                        .requestMatchers("/supplier/**", "/api/v1/supplier/**").hasRole("SUPPLIER")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .formLogin(form -> form.defaultSuccessUrl("/", false))
                // Basic auth makes the read-only admin log endpoint convenient for local curl diagnostics.
                .httpBasic(Customizer.withDefaults())
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
