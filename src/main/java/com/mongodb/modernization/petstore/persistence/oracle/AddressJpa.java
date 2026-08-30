package com.mongodb.modernization.petstore.persistence.oracle;

import com.mongodb.modernization.petstore.shared.domain.Address;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class AddressJpa {
    @Column(name = "SHIP_FULL_NAME", nullable = false, length = 100) String fullName;
    @Column(name = "SHIP_LINE1", nullable = false, length = 150) String line1;
    @Column(name = "SHIP_LINE2", length = 150) String line2;
    @Column(name = "SHIP_CITY", nullable = false, length = 80) String city;
    @Column(name = "SHIP_STATE", nullable = false, length = 80) String state;
    @Column(name = "SHIP_POSTAL_CODE", nullable = false, length = 20) String postalCode;
    @Column(name = "SHIP_COUNTRY", nullable = false, length = 80) String country;

    /** Creates a address jpa and wires its required collaborators. */
    protected AddressJpa() {}
    /** Creates a address jpa and wires its required collaborators. */
    AddressJpa(Address address) {
        fullName = address.fullName(); line1 = address.line1(); line2 = address.line2(); city = address.city();
        state = address.state(); postalCode = address.postalCode(); country = address.country();
    }
    /** Maps this persistence representation to the corresponding domain model. */
    Address toDomain() { return new Address(fullName, line1, line2 == null ? "" : line2, city, state, postalCode, country); }
}
