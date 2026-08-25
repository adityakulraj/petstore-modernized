package com.mongodb.modernization.petstore.config;

import org.springframework.security.config.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService users(AppProperties properties, PasswordEncoder encoder) {
        var demo = properties.demo();
        var primary = User.withUsername(demo.username())
                .password(encoder.encode(demo.password()))
                .roles("CUSTOMER")
                .build();
        var additional = User.withUsername(demo.additionalUsername())
                .password(encoder.encode(demo.additionalPassword()))
                .roles("CUSTOMER")
                .build();
        var admin = User.withUsername(properties.admin().username())
                .password(encoder.encode(properties.admin().password()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(primary, additional, admin);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/api/v1/catalog/**", "/api/v1/session",
                                "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .formLogin(form -> form.defaultSuccessUrl("/", false))
                // Basic auth makes the read-only admin log endpoint convenient for local curl diagnostics.
                .httpBasic(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessUrl("/"))
                .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; " +
                                "object-src 'none'; base-uri 'self'; frame-ancestors 'none'")))
                .build();
    }
}
