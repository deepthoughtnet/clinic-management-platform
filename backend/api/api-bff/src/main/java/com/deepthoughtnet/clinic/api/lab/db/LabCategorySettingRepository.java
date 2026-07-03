package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabCategorySettingRepository extends JpaRepository<LabCategorySettingEntity, UUID> {
    List<LabCategorySettingEntity> findByTenantIdOrderByDisplayOrderAscCategoryCodeAsc(UUID tenantId);
    Optional<LabCategorySettingEntity> findByTenantIdAndCategoryCodeIgnoreCase(UUID tenantId, String categoryCode);
}
