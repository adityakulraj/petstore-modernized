package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaCustomerOrderCommandRepository extends JpaRepository<CustomerOrderCommandJpaEntity, String> {}
