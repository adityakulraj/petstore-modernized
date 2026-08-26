package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface JpaCatalogChangeRepository extends JpaRepository<CatalogChangeJpaEntity, String> {
    List<CatalogChangeJpaEntity> findAllByOrderByOccurredAtDesc();
}
