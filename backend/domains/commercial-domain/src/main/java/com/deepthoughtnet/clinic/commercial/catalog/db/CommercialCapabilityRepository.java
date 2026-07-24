package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommercialCapabilityRepository extends JpaRepository<CommercialCapabilityEntity, UUID>, JpaSpecificationExecutor<CommercialCapabilityEntity> {
    boolean existsByCodeIgnoreCase(String code);

    Optional<CommercialCapabilityEntity> findByCodeIgnoreCase(String code);
}
