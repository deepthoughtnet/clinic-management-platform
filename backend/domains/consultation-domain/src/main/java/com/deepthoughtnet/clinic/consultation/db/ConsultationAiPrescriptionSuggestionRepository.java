package com.deepthoughtnet.clinic.consultation.db;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationAiPrescriptionSuggestionRepository extends JpaRepository<ConsultationAiPrescriptionSuggestionEntity, UUID> {
    Optional<ConsultationAiPrescriptionSuggestionEntity> findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(UUID tenantId, UUID consultationId);
}
