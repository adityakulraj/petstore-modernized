package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.accounts.domain.CustomerAccount;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("customerAccounts")
class CustomerAccountDocument {
    @Id String username;
    String passwordHash; String fullName; String email; String phone;
    AddressDocument defaultAddress;
    String preferredLanguage; String favoriteCategory;
    boolean myListPreference; boolean bannerPreference;

    /** Creates a customer account document and wires its required collaborators. */
    CustomerAccountDocument() {}
    /** Creates a customer account document and wires its required collaborators. */
    CustomerAccountDocument(CustomerAccount account) {
        username = account.username(); passwordHash = account.passwordHash(); fullName = account.fullName(); email = account.email();
        phone = account.phone(); defaultAddress = new AddressDocument(account.defaultAddress());
        preferredLanguage = account.preferredLanguage(); favoriteCategory = account.favoriteCategory();
        myListPreference = account.myListPreference(); bannerPreference = account.bannerPreference();
    }
    /** Maps this persistence representation to the corresponding domain model. */
    CustomerAccount toDomain() { return new CustomerAccount(username, passwordHash, fullName, email, phone,
            defaultAddress.toDomain(), preferredLanguage, favoriteCategory, myListPreference, bannerPreference); }
}
