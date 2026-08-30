package com.mongodb.modernization.petstore.accounts.domain;

import com.mongodb.modernization.petstore.shared.domain.Address;

/** Customer-owned account data. Passwords are stored only as BCrypt hashes. */
public record CustomerAccount(String username, String passwordHash, String fullName, String email, String phone,
                              Address defaultAddress, String preferredLanguage, String favoriteCategory,
                              boolean myListPreference, boolean bannerPreference) {
    /** Returns a profile-updated copy while preserving username and password hash. */
    public CustomerAccount withProfile(String fullName, String email, String phone, Address address,
                                       String preferredLanguage, String favoriteCategory,
                                       boolean myListPreference, boolean bannerPreference) {
        return new CustomerAccount(username, passwordHash, fullName, email, phone, address, preferredLanguage,
                favoriteCategory, myListPreference, bannerPreference);
    }
}
