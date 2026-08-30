package com.mongodb.modernization.petstore.persistence.mongo;

import com.mongodb.modernization.petstore.shared.domain.Address;

class AddressDocument {
    String fullName; String line1; String line2; String city; String state; String postalCode; String country;
    /** Creates a address document and wires its required collaborators. */
    AddressDocument() {}
    /** Creates a address document and wires its required collaborators. */
    AddressDocument(Address value) {
        fullName = value.fullName(); line1 = value.line1(); line2 = value.line2(); city = value.city();
        state = value.state(); postalCode = value.postalCode(); country = value.country();
    }
    /** Maps this persistence representation to the corresponding domain model. */
    Address toDomain() { return new Address(fullName, line1, line2, city, state, postalCode, country); }
}
