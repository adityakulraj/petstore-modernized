package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "PS_CUSTOMER_ACCOUNT")
class CustomerAccountJpaEntity {
    @Id @Column(length = 50) String username;
    @Column(name = "PASSWORD_HASH", nullable = false, length = 100) String passwordHash;
    @Column(name = "FULL_NAME", nullable = false, length = 100) String fullName;
    @Column(nullable = false, length = 254) String email;
    @Column(nullable = false, length = 40) String phone;
    @Embedded AddressJpa defaultAddress;
    @Column(nullable = false, length = 10) String preferredLanguage;
    @Column(nullable = false, length = 30) String favoriteCategory;
    @Column(nullable = false) boolean myListPreference;
    @Column(nullable = false) boolean bannerPreference;

    /** Creates a customer account jpa entity and wires its required collaborators. */
    protected CustomerAccountJpaEntity() {}
    /** Creates a customer account jpa entity and wires its required collaborators. */
    CustomerAccountJpaEntity(CustomerAccount account) { replaceWith(account); }
    /** Copies the supplied domain state into this mutable persistence representation. */
    void replaceWith(CustomerAccount account) {
        username = account.username(); passwordHash = account.passwordHash(); fullName = account.fullName();
        email = account.email(); phone = account.phone(); defaultAddress = new AddressJpa(account.defaultAddress());
        preferredLanguage = account.preferredLanguage(); favoriteCategory = account.favoriteCategory();
        myListPreference = account.myListPreference(); bannerPreference = account.bannerPreference();
    }
    /** Maps this persistence representation to the corresponding domain model. */
    CustomerAccount toDomain() { return new CustomerAccount(username, passwordHash, fullName, email, phone,
            defaultAddress.toDomain(), preferredLanguage, favoriteCategory, myListPreference, bannerPreference); }
}
