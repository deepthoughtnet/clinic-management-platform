package com.deepthoughtnet.clinic.commercial.catalog.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialCapabilityModuleRepository extends JpaRepository<CommercialCapabilityModuleEntity, CommercialCapabilityModuleId> {
    List<CommercialCapabilityModuleEntity> findByCapability_IdOrderByDisplayOrderAsc( UUID capabilityId);

    void deleteByCapability_Id(UUID capabilityId);
}
