package com.deepthoughtnet.clinic.consultation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRepository extends JpaRepository<ConsultationEntity, UUID> {
    Optional<ConsultationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ConsultationEntity> findByTenantIdAndAppointmentId(UUID tenantId, UUID appointmentId);

    List<ConsultationEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);

    List<ConsultationEntity> findByTenantIdAndDoctorUserId(UUID tenantId, UUID doctorUserId);

    List<ConsultationEntity> findByTenantIdAndDoctorUserIdOrderByCreatedAtDesc(UUID tenantId, UUID doctorUserId);

    boolean existsByTenantIdAndIdAndDoctorUserId(UUID tenantId, UUID id, UUID doctorUserId);

    boolean existsByTenantIdAndPatientIdAndDoctorUserId(UUID tenantId, UUID patientId, UUID doctorUserId);

    List<ConsultationEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
