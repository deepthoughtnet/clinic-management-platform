package com.deepthoughtnet.clinic.api.clinicalintake.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientClinicalIntakeRepository extends JpaRepository<PatientClinicalIntakeEntity, UUID> {
    List<PatientClinicalIntakeEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);

    List<PatientClinicalIntakeEntity> findByTenantIdAndPatientIdAndAppointmentIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId, UUID appointmentId);

    Optional<PatientClinicalIntakeEntity> findFirstByTenantIdAndPatientIdAndAppointmentIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId, UUID appointmentId);
}
