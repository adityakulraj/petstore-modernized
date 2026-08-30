package com.mongodb.modernization.petstore.persistence.oracle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface JpaCatalogChangeRepository extends JpaRepository<CatalogChangeJpaEntity, String> {
    /** Executes the find all by order by occurred at desc persistence operation against the selected database. */
    List<CatalogChangeJpaEntity> findAllByOrderByOccurredAtDesc();
}
