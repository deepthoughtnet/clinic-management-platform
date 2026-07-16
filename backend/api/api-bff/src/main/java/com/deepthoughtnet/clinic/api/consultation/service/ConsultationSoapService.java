package com.deepthoughtnet.clinic.api.consultation.service;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationSoapRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationSoapResponse;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteEntity.ConsultationSoapSource;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteEntity.ConsultationSoapStatus;
import com.deepthoughtnet.clinic.consultation.db.ConsultationSoapNoteRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConsultationSoapService {
    private static final String ENTITY_TYPE = "CONSULTATION_SOAP_NOTE";

    private final ConsultationService consultationService;
    private final ClinicalContextService clinicalContextService;
    private final ConsultationSoapContextHasher contextHasher;
    private final ConsultationSoapNoteRepository repository;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public ConsultationSoapService(
            ConsultationService consultationService,
            ClinicalContextService clinicalContextService,
            ConsultationSoapContextHasher contextHasher,
            ConsultationSoapNoteRepository repository,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.consultationService = consultationService;
        this.clinicalContextService = clinicalContextService;
        this.contextHasher = contextHasher;
        this.repository = repository;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ConsultationSoapResponse get(UUID tenantId, UUID consultationId) {
        ConsultationRecord consultation = requireConsultation(tenantId, consultationId);
        String currentSourceHash = currentSourceHash(tenantId, consultation);
        return repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId)
                .map(entity -> toResponse(entity, currentSourceHash))
                .orElseGet(() -> emptyResponse(consultationId, currentSourceHash));
    }

    @Transactional
    public ConsultationSoapResponse saveManual(UUID tenantId, UUID consultationId, ConsultationSoapRequest request) {
        ConsultationRecord consultation = requireConsultation(tenantId, consultationId);
        String currentSourceHash = currentSourceHash(tenantId, consultation);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return persistNewVersion(
                tenantId,
                consultationId,
                request,
                ConsultationSoapStatus.DRAFT,
                ConsultationSoapSource.MANUAL,
                actorAppUserId,
                null,
                null,
                null,
                null,
                currentSourceHash,
                "consultation.soap.saved",
                "Saved SOAP note"
        );
    }

    @Transactional
    public ConsultationSoapResponse acceptAiDraft(UUID tenantId, UUID consultationId, ConsultationSoapRequest request) {
        ConsultationRecord consultation = requireConsultation(tenantId, consultationId);
        String currentSourceHash = currentSourceHash(tenantId, consultation);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return persistNewVersion(
                tenantId,
                consultationId,
                request,
                ConsultationSoapStatus.ACCEPTED,
                ConsultationSoapSource.AI_ACCEPTED,
                actorAppUserId,
                normalize(request == null ? null : request.aiProvider()),
                normalize(request == null ? null : request.aiModel()),
                actorAppUserId,
                request == null || request.generatedAt() == null ? OffsetDateTime.now() : request.generatedAt(),
                currentSourceHash,
                "consultation.soap.accepted_ai_draft",
                "Accepted AI SOAP draft"
        );
    }

    private ConsultationSoapResponse persistNewVersion(UUID tenantId,
                                                       UUID consultationId,
                                                       ConsultationSoapRequest request,
                                                       ConsultationSoapStatus status,
                                                       ConsultationSoapSource source,
                                                       UUID generatedByAppUserId,
                                                       String aiProvider,
                                                       String aiModel,
                                                       UUID acceptedByAppUserId,
                                                       OffsetDateTime generatedAt,
                                                       String sourceHash,
                                                       String action,
                                                       String summary) {
        ConsultationSoapNoteEntity previous = repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId).orElse(null);
        int versionNumber = previous == null ? 1 : previous.getVersionNumber() + 1;
        OffsetDateTime acceptedAt = status == ConsultationSoapStatus.ACCEPTED ? OffsetDateTime.now() : null;
        ConsultationSoapNoteEntity entity = ConsultationSoapNoteEntity.create(
                tenantId,
                consultationId,
                versionNumber,
                status,
                source,
                normalize(request == null ? null : request.subjective()),
                normalize(request == null ? null : request.objective()),
                normalize(request == null ? null : request.assessment()),
                normalize(request == null ? null : request.plan()),
                aiProvider,
                aiModel,
                generatedByAppUserId,
                acceptedByAppUserId,
                generatedAt,
                acceptedAt,
                sourceHash
        );
        ConsultationSoapNoteEntity saved = repository.save(entity);
        if (previous != null) {
            previous.supersede(saved.getId());
            repository.save(previous);
        }
        audit(tenantId, saved, action, summary, previous);
        return toResponse(saved, sourceHash);
    }

    private void audit(UUID tenantId, ConsultationSoapNoteEntity entity, String action, String summary, ConsultationSoapNoteEntity previous) {
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                entity.getId(),
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                summary,
                detailsJson(entity, previous)
        ));
    }

    private String detailsJson(ConsultationSoapNoteEntity entity, ConsultationSoapNoteEntity previous) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", entity.getId());
        details.put("tenantId", entity.getTenantId());
        details.put("consultationId", entity.getConsultationId());
        details.put("versionNumber", entity.getVersionNumber());
        details.put("status", entity.getStatus());
        details.put("source", entity.getSource());
        details.put("generatedByAppUserId", entity.getGeneratedByAppUserId());
        details.put("acceptedByAppUserId", entity.getAcceptedByAppUserId());
        details.put("generatedAt", entity.getGeneratedAt());
        details.put("acceptedAt", entity.getAcceptedAt());
        details.put("supersededPreviousId", previous == null ? null : previous.getId());
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + entity.getId() + "\"}";
        }
    }

    private ConsultationSoapResponse toResponse(ConsultationSoapNoteEntity entity, String currentSourceHash) {
        return new ConsultationSoapResponse(
                entity.getId() == null ? null : entity.getId().toString(),
                entity.getConsultationId() == null ? null : entity.getConsultationId().toString(),
                entity.getVersionNumber(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getSource() == null ? null : entity.getSource().name(),
                entity.getSubjective(),
                entity.getObjective(),
                entity.getAssessment(),
                entity.getPlan(),
                entity.getAiProvider(),
                entity.getAiModel(),
                entity.getGeneratedByAppUserId() == null ? null : entity.getGeneratedByAppUserId().toString(),
                entity.getAcceptedByAppUserId() == null ? null : entity.getAcceptedByAppUserId().toString(),
                entity.getSourceHash(),
                currentSourceHash,
                entity.getStatus() == ConsultationSoapStatus.ACCEPTED && sourceChanged(entity.getSourceHash(), currentSourceHash),
                entity.getGeneratedAt(),
                entity.getAcceptedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ConsultationSoapResponse emptyResponse(UUID consultationId, String currentSourceHash) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ConsultationSoapResponse(
                null,
                consultationId == null ? null : consultationId.toString(),
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                currentSourceHash,
                false,
                null,
                null,
                now,
                now
        );
    }

    private ConsultationRecord requireConsultation(UUID tenantId, UUID consultationId) {
        return consultationService.findById(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
    }

    private String currentSourceHash(UUID tenantId, ConsultationRecord consultation) {
        ClinicalContextResponse context = clinicalContextService.buildClinicalContext(tenantId, consultation.patientId(), consultation.id());
        return contextHasher.sourceHash(consultation, context);
    }

    private boolean sourceChanged(String sourceHash, String currentSourceHash) {
        return sourceHash != null && currentSourceHash != null && !sourceHash.equals(currentSourceHash);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
