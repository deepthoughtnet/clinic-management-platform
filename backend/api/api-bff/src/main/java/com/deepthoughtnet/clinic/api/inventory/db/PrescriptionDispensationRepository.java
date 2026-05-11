package com.deepthoughtnet.clinic.api.inventory.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionDispensationRepository extends JpaRepository<PrescriptionDispensationEntity, UUID> {
    Optional<PrescriptionDispensationEntity> findByTenantIdAndPrescriptionId(UUID tenantId, UUID prescriptionId);
}
