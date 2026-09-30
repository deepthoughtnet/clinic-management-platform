package com.deepthoughtnet.clinic.inventory.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefillRequestRepository extends JpaRepository<RefillRequestEntity, UUID> {
    Optional<RefillRequestEntity> findByTenantIdAndPatientIdAndPrescriptionIdAndRefillCycle(
            UUID tenantId, UUID patientId, UUID prescriptionId, String refillCycle);
    List<RefillRequestEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);
}
