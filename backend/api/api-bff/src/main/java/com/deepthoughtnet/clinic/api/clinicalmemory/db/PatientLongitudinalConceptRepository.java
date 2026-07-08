package com.deepthoughtnet.clinic.api.clinicalmemory.db;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientLongitudinalConceptRepository extends JpaRepository<PatientLongitudinalConceptEntity, UUID> {
    List<PatientLongitudinalConceptEntity> findByTenantIdAndPatientIdOrderByObservedAtDescCreatedAtDesc(UUID tenantId, UUID patientId);

    List<PatientLongitudinalConceptEntity> findByTenantIdAndPatientIdAndSourceDocumentIdOrderByCreatedAtAsc(UUID tenantId, UUID patientId, UUID sourceDocumentId);

    @Modifying
    @Query("""
            delete from PatientLongitudinalConceptEntity c
            where c.tenantId = :tenantId
              and c.patientId = :patientId
              and c.sourceDocumentId = :sourceDocumentId
              and c.verificationStatus = :status
            """)
    int deleteByDocumentAndStatus(@Param("tenantId") UUID tenantId,
                                  @Param("patientId") UUID patientId,
                                  @Param("sourceDocumentId") UUID sourceDocumentId,
                                  @Param("status") String status);
}
