package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderResultRepository extends JpaRepository<LabOrderResultEntity, UUID> {
    List<LabOrderResultEntity> findByTenantIdAndLabOrderIdOrderBySortOrderAscCreatedAtAsc(UUID tenantId, UUID labOrderId);
    List<LabOrderResultEntity> findByTenantIdAndLabOrderItemId(UUID tenantId, UUID labOrderItemId);
    void deleteByTenantIdAndLabOrderId(UUID tenantId, UUID labOrderId);
}
