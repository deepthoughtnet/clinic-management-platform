package com.deepthoughtnet.clinic.consultation.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationAiSummaryRepository extends JpaRepository<ConsultationAiSummaryEntity, UUID> {
    Optional<ConsultationAiSummaryEntity> findByTenantIdAndConsultationId(UUID tenantId, UUID consultationId);
}
