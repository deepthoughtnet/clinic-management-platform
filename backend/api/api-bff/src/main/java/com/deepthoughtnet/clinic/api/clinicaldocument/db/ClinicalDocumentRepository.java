package com.deepthoughtnet.clinic.api.clinicaldocument.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocumentEntity, UUID> {
    Optional<ClinicalDocumentEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<ClinicalDocumentEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);
    boolean existsByTenantIdAndStorageKey(UUID tenantId, String storageKey);
}
