package com.mongodb.modernization.petstore.accounts.api;

import com.mongodb.modernization.petstore.accounts.application.CustomerAccountService;
import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import com.mongodb.modernization.petstore.shared.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class CustomerAccountController {
    private final CustomerAccountService accounts;

    /** Creates a customer account controller and wires its required collaborators. */
    public CustomerAccountController(CustomerAccountService accounts) { this.accounts = accounts; }

    @PostMapping
    /** Handles the register HTTP request and returns its API response. */
    public AccountResponse register(@Valid @RequestBody RegistrationRequest request) {
        return AccountResponse.from(accounts.register(request.username(), request.password(), request.fullName(), request.email(),
                request.phone(), request.address().toDomain(), request.preferredLanguage(), request.favoriteCategory(),
                request.myListPreference(), request.bannerPreference()));
    }

    @GetMapping("/me")
    /** Handles the me HTTP request and returns its API response. */
    public AccountResponse me(@AuthenticationPrincipal UserDetails user) { return AccountResponse.from(accounts.account(user.getUsername())); }

    @PutMapping("/me")
    /** Handles the update HTTP request and returns its API response. */
    public AccountResponse update(@AuthenticationPrincipal UserDetails user, @Valid @RequestBody ProfileRequest request) {
        return AccountResponse.from(accounts.update(user.getUsername(), request.fullName(), request.email(), request.phone(),
                request.address().toDomain(), request.preferredLanguage(), request.favoriteCategory(),
                request.myListPreference(), request.bannerPreference()));
    }

    /** Handles the registration request HTTP request and returns its API response. */
    public record RegistrationRequest(@Pattern(regexp = "[A-Za-z0-9_.-]{3,50}") String username,
                                      @Size(min = 12, max = 100) String password,
                                      @NotBlank @Size(max = 100) String fullName, @Email @Size(max = 254) String email,
                                      @NotBlank @Size(max = 40) String phone, @NotNull @Valid AddressRequest address,
                                      @NotBlank @Size(max = 10) String preferredLanguage,
                                      @NotBlank @Size(max = 30) String favoriteCategory,
                                      boolean myListPreference, boolean bannerPreference) {}
    /** Handles the profile request HTTP request and returns its API response. */
    public record ProfileRequest(@NotBlank @Size(max = 100) String fullName, @Email @Size(max = 254) String email,
                                 @NotBlank @Size(max = 40) String phone, @NotNull @Valid AddressRequest address,
                                 @NotBlank @Size(max = 10) String preferredLanguage,
                                 @NotBlank @Size(max = 30) String favoriteCategory,
                                 boolean myListPreference, boolean bannerPreference) {}
    /** Handles the address request HTTP request and returns its API response. */
    public record AddressRequest(@NotBlank @Size(max = 100) String fullName, @NotBlank @Size(max = 150) String line1,
                                 @Size(max = 150) String line2, @NotBlank @Size(max = 80) String city,
                                 @NotBlank @Size(max = 80) String state, @NotBlank @Size(max = 20) String postalCode,
                                 @NotBlank @Size(max = 80) String country) {
        /** Maps this persistence representation to the corresponding domain model. */
        Address toDomain() { return new Address(fullName, line1, line2, city, state, postalCode, country); }
    }
    /** Handles the account response HTTP request and returns its API response. */
    public record AccountResponse(String username, String fullName, String email, String phone, Address address,
                                  String preferredLanguage, String favoriteCategory, boolean myListPreference,
                                  boolean bannerPreference) {
        /** Handles the from HTTP request and returns its API response. */
        static AccountResponse from(CustomerAccount account) { return new AccountResponse(account.username(), account.fullName(),
                account.email(), account.phone(), account.defaultAddress(), account.preferredLanguage(),
                account.favoriteCategory(), account.myListPreference(), account.bannerPreference()); }
    }
}
