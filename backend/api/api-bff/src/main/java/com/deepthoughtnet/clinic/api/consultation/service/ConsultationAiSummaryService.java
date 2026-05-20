package com.deepthoughtnet.clinic.api.consultation.service;

import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiSummaryRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiSummaryResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiSummaryEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiSummaryRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConsultationAiSummaryService {
    private final ConsultationService consultationService;
    private final ConsultationAiSummaryRepository repository;

    public ConsultationAiSummaryService(ConsultationService consultationService, ConsultationAiSummaryRepository repository) {
        this.consultationService = consultationService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ConsultationAiSummaryResponse get(UUID tenantId, UUID consultationId) {
        requireConsultation(tenantId, consultationId);
        ConsultationAiSummaryEntity entity = repository.findByTenantIdAndConsultationId(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("AI summary not found"));
        return toResponse(entity);
    }

    @Transactional
    public ConsultationAiSummaryResponse save(UUID tenantId, UUID consultationId, ConsultationAiSummaryRequest request) {
        requireConsultation(tenantId, consultationId);
        if (request == null || !StringUtils.hasText(request.summary())) {
            throw new IllegalArgumentException("summary is required");
        }
        ConsultationAiSummaryEntity entity = repository.findByTenantIdAndConsultationId(tenantId, consultationId)
                .orElseGet(() -> ConsultationAiSummaryEntity.create(tenantId, consultationId));
        entity.update(
                request.summary().trim(),
                normalize(request.provider()),
                normalize(request.model()),
                request.generatedAt() == null ? OffsetDateTime.now() : request.generatedAt()
        );
        return toResponse(repository.save(entity));
    }

    private void requireConsultation(UUID tenantId, UUID consultationId) {
        consultationService.findById(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
    }

    private ConsultationAiSummaryResponse toResponse(ConsultationAiSummaryEntity entity) {
        return new ConsultationAiSummaryResponse(
                entity.getConsultationId() == null ? null : entity.getConsultationId().toString(),
                entity.getSummary(),
                entity.getProvider(),
                entity.getModel(),
                entity.getGeneratedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
