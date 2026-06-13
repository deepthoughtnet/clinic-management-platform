package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTestMasterRepository extends JpaRepository<LabTestMasterEntity, UUID> {
    Optional<LabTestMasterEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<LabTestMasterEntity> findByTenantIdAndTestCodeIgnoreCase(UUID tenantId, String testCode);
    Optional<LabTestMasterEntity> findByTenantIdAndTestNameIgnoreCase(UUID tenantId, String testName);
    List<LabTestMasterEntity> findByTenantIdOrderByTestNameAsc(UUID tenantId);
    List<LabTestMasterEntity> findByTenantIdAndActiveTrueOrderByTestNameAsc(UUID tenantId);
}
