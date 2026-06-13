package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabTestParameterRepository extends JpaRepository<LabTestParameterEntity, UUID> {
    List<LabTestParameterEntity> findByTenantIdAndLabTestIdOrderBySortOrderAsc(UUID tenantId, UUID labTestId);
    void deleteByTenantIdAndLabTestId(UUID tenantId, UUID labTestId);
}
