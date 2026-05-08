package com.deepthoughtnet.clinic.api.clinicaldocument.ai.db;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalAiJobRepository extends JpaRepository<ClinicalAiJobEntity, UUID> {
    List<ClinicalAiJobEntity> findTop25ByStatusInOrderByCreatedAtAsc(Collection<ClinicalAiJobStatus> statuses);

    Optional<ClinicalAiJobEntity> findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(UUID tenantId, UUID documentId, com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobType jobType);
}
