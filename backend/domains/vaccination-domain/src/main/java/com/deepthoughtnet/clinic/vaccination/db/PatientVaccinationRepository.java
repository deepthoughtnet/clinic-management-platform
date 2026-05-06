package com.deepthoughtnet.clinic.vaccination.db;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientVaccinationRepository extends JpaRepository<PatientVaccinationEntity, UUID> {
    Optional<PatientVaccinationEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<PatientVaccinationEntity> findByTenantIdAndPatientIdOrderByGivenDateDesc(UUID tenantId, UUID patientId);

    List<PatientVaccinationEntity> findByTenantIdOrderByGivenDateDesc(UUID tenantId);

    List<PatientVaccinationEntity> findByTenantIdAndNextDueDateBetweenOrderByNextDueDateAscGivenDateDesc(
            UUID tenantId,
            LocalDate from,
            LocalDate to
    );

    List<PatientVaccinationEntity> findByTenantIdAndNextDueDateLessThanOrderByNextDueDateAscGivenDateDesc(
            UUID tenantId,
            LocalDate date
    );
}
