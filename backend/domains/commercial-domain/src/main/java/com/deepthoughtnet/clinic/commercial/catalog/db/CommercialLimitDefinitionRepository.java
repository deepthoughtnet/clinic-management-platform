package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommercialLimitDefinitionRepository extends JpaRepository<CommercialLimitDefinitionEntity, UUID>, JpaSpecificationExecutor<CommercialLimitDefinitionEntity> {
    boolean existsByCodeIgnoreCase(String code);

    Optional<CommercialLimitDefinitionEntity> findByCodeIgnoreCase(String code);
}
