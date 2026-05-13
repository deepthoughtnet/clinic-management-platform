package com.deepthoughtnet.clinic.patient.db;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PatientRepository extends JpaRepository<PatientEntity, UUID>, JpaSpecificationExecutor<PatientEntity> {
    Optional<PatientEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<PatientEntity> findByTenantIdAndPatientNumber(UUID tenantId, String patientNumber);

    Optional<PatientEntity> findFirstByTenantIdAndMobileIgnoreCaseAndActiveTrue(UUID tenantId, String mobile);

    Optional<PatientEntity> findFirstByTenantIdAndEmailIgnoreCaseAndActiveTrue(UUID tenantId, String email);

    boolean existsByTenantIdAndPatientNumber(UUID tenantId, String patientNumber);

    List<PatientEntity> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    List<PatientEntity> findByTenantIdAndActiveTrue(UUID tenantId);
}
