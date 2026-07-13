package com.deepthoughtnet.clinic.consultation.db;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicalReasoningResultRepository extends JpaRepository<ClinicalReasoningResultEntity, UUID> {
    Optional<ClinicalReasoningResultEntity> findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(UUID tenantId, UUID consultationId);

    List<ClinicalReasoningResultEntity> findByTenantIdAndConsultationIdOrderByVersionNumberDesc(UUID tenantId, UUID consultationId);
}
