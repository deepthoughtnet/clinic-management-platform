package com.deepthoughtnet.clinic.api.inventory.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionDispenseItemRepository extends JpaRepository<PrescriptionDispenseItemEntity, UUID> {
    List<PrescriptionDispenseItemEntity> findByTenantIdAndPrescriptionIdOrderByPrescribedSortOrderAsc(UUID tenantId, UUID prescriptionId);
}
