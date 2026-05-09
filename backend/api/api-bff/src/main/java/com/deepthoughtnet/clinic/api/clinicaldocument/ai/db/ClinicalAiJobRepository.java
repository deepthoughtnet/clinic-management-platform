package com.deepthoughtnet.clinic.api.clinicaldocument.ai.db;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicalAiJobRepository extends JpaRepository<ClinicalAiJobEntity, UUID> {
    List<ClinicalAiJobEntity> findTop25ByStatusInOrderByCreatedAtAsc(Collection<ClinicalAiJobStatus> statuses);

    Optional<ClinicalAiJobEntity> findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(UUID tenantId, UUID documentId, com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobType jobType);

    long countByTenantIdAndCreatedAtBetween(UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    long countByTenantIdAndStatusInAndCreatedAtBetween(UUID tenantId, Collection<ClinicalAiJobStatus> statuses, OffsetDateTime from, OffsetDateTime to);

    long countByTenantIdAndAttemptCountGreaterThanAndCreatedAtBetween(UUID tenantId, int attemptCount, OffsetDateTime from, OffsetDateTime to);

    @Query("""
            select coalesce(avg(j.confidence), 0)
            from ClinicalAiJobEntity j
            where j.tenantId = :tenantId
              and j.createdAt between :from and :to
              and j.confidence is not null
            """)
    BigDecimal averageConfidence(@Param("tenantId") UUID tenantId,
                                 @Param("from") OffsetDateTime from,
                                 @Param("to") OffsetDateTime to);
}
