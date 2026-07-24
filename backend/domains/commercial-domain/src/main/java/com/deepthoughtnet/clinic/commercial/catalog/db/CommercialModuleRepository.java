package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CommercialModuleRepository extends JpaRepository<CommercialModuleEntity, UUID>, JpaSpecificationExecutor<CommercialModuleEntity> {
    boolean existsByCodeIgnoreCase(String code);

    Optional<CommercialModuleEntity> findByCodeIgnoreCase(String code);

    List<CommercialModuleEntity> findByRuntimeModuleCodeIgnoreCase(String runtimeModuleCode);
}
