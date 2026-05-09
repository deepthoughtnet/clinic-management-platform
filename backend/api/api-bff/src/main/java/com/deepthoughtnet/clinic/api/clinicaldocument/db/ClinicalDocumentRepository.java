package com.deepthoughtnet.clinic.api.clinicaldocument.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocumentEntity, UUID> {
    Optional<ClinicalDocumentEntity> findByTenantIdAndId(UUID tenantId, UUID id);
    List<ClinicalDocumentEntity> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId);
    boolean existsByTenantIdAndStorageKey(UUID tenantId, String storageKey);

    @Query("""
            select count(d)
            from ClinicalDocumentEntity d
            where d.tenantId = :tenantId
              and d.createdAt between :from and :to
            """)
    long countByTenantIdAndCreatedAtBetween(@Param("tenantId") UUID tenantId,
                                           @Param("from") java.time.OffsetDateTime from,
                                           @Param("to") java.time.OffsetDateTime to);

    long countByTenantIdAndAiExtractionStatusAndCreatedAtBetween(UUID tenantId, String aiExtractionStatus, java.time.OffsetDateTime from, java.time.OffsetDateTime to);
}
