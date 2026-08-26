package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaCustomerAccountRepository extends JpaRepository<CustomerAccountJpaEntity, String> {}
