package com.deepthoughtnet.clinic.api.lab.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabOrderItemRepository extends JpaRepository<LabOrderItemEntity, UUID> {
    List<LabOrderItemEntity> findByTenantIdAndLabOrderIdOrderBySortOrderAsc(UUID tenantId, UUID labOrderId);
    void deleteByTenantIdAndLabOrderId(UUID tenantId, UUID labOrderId);
}
