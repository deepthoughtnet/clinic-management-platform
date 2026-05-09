package com.deepthoughtnet.clinic.prescription.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<PrescriptionEntity, UUID> {
    Optional<PrescriptionEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<PrescriptionEntity> findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(UUID tenantId, UUID consultationId);

    List<PrescriptionEntity> findByTenantIdAndConsultationIdOrderByVersionNumberAsc(UUID tenantId, UUID consultationId);

    List<PrescriptionEntity> findByTenantIdAndConsultationIdOrderByVersionNumberDesc(UUID tenantId, UUID consultationId);

    Optional<PrescriptionEntity> findFirstByTenantIdAndParentPrescriptionIdOrderByVersionNumberDesc(UUID tenantId, UUID parentPrescriptionId);

    Optional<PrescriptionEntity> findByTenantIdAndPrescriptionNumber(UUID tenantId, String prescriptionNumber);

    List<PrescriptionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<PrescriptionEntity> findByTenantIdAndDoctorUserIdOrderByCreatedAtDesc(UUID tenantId, UUID doctorUserId);

    List<PrescriptionEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);
}
