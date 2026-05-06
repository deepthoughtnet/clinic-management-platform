package com.deepthoughtnet.clinic.identity.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantModuleRepository extends JpaRepository<TenantModuleEntity, UUID> {
    Optional<TenantModuleEntity> findByTenantIdAndModuleCode(UUID tenantId, String moduleCode);
    List<TenantModuleEntity> findByTenantId(UUID tenantId);
}
