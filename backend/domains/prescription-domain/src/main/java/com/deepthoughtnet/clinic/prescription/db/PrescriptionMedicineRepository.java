package com.deepthoughtnet.clinic.prescription.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionMedicineRepository extends JpaRepository<PrescriptionMedicineEntity, UUID> {
    List<PrescriptionMedicineEntity> findByTenantIdAndPrescriptionIdOrderBySortOrderAsc(UUID tenantId, UUID prescriptionId);

    void deleteByTenantIdAndPrescriptionId(UUID tenantId, UUID prescriptionId);
}
