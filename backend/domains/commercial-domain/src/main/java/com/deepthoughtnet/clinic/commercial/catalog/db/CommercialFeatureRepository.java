package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommercialFeatureRepository extends JpaRepository<CommercialFeatureEntity, UUID>, JpaSpecificationExecutor<CommercialFeatureEntity> {
    boolean existsByCodeIgnoreCase(String code);

    Optional<CommercialFeatureEntity> findByCodeIgnoreCase(String code);
}
