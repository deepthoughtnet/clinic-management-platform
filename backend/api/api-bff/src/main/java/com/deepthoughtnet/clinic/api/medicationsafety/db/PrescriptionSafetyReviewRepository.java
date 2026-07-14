package com.deepthoughtnet.clinic.api.medicationsafety.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionSafetyReviewRepository extends JpaRepository<PrescriptionSafetyReviewEntity, UUID> {
    Optional<PrescriptionSafetyReviewEntity> findFirstByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(UUID tenantId, UUID prescriptionId);

    List<PrescriptionSafetyReviewEntity> findByTenantIdAndPrescriptionIdOrderByUpdatedAtDesc(UUID tenantId, UUID prescriptionId);
}
