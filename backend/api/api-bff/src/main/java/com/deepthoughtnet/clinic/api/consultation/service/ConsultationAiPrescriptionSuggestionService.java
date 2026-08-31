package com.deepthoughtnet.clinic.api.consultation.service;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionResponse;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationAiPrescriptionSuggestionResponse.Item;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderRecord;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiPrescriptionSuggestionEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationAiPrescriptionSuggestionRepository;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConsultationAiPrescriptionSuggestionService {
    private static final String ENTITY_TYPE = "CONSULTATION_AI_PRESCRIPTION_SUGGESTION";

    private final ConsultationService consultationService;
    private final ClinicalContextService clinicalContextService;
    private final LabService labService;
    private final ConsultationAiPrescriptionSuggestionRepository repository;
    private final ConsultationAiPrescriptionSuggestionContextHasher contextHasher;
    private final TenantUserManagementService tenantUserManagementService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public ConsultationAiPrescriptionSuggestionService(
            ConsultationService consultationService,
            ClinicalContextService clinicalContextService,
            LabService labService,
            ConsultationAiPrescriptionSuggestionRepository repository,
            ConsultationAiPrescriptionSuggestionContextHasher contextHasher,
            TenantUserManagementService tenantUserManagementService,
            AuditEventPublisher auditEventPublisher,
            ObjectMapper objectMapper
    ) {
        this.consultationService = consultationService;
        this.clinicalContextService = clinicalContextService;
        this.labService = labService;
        this.repository = repository;
        this.contextHasher = contextHasher;
        this.tenantUserManagementService = tenantUserManagementService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional(readOnly = true)
    public ConsultationAiPrescriptionSuggestionResponse get(UUID tenantId, UUID consultationId) {
        ConsultationRecord consultation = requireConsultation(tenantId, consultationId);
        ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(tenantId, consultation.patientId(), consultationId);
        ConsultationAiPrescriptionSuggestionEntity latest = latestSuggestion(tenantId, consultationId);
        String currentSourceHash = currentSourceHash(consultation, clinicalContext, currentLabOrders(tenantId, consultationId));
        if (latest == null) {
            return emptyResponse(consultationId, currentSourceHash);
        }
        return toResponse(latest, currentSourceHash);
    }

    @Transactional
    public ConsultationAiPrescriptionSuggestionResponse save(UUID tenantId, UUID consultationId, ConsultationAiPrescriptionSuggestionRequest request) {
        ConsultationRecord consultation = requireConsultation(tenantId, consultationId);
        ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(tenantId, consultation.patientId(), consultationId);
        List<LabOrderRecord> labOrders = currentLabOrders(tenantId, consultationId);
        String currentSourceHash = currentSourceHash(consultation, clinicalContext, labOrders);
        ConsultationAiPrescriptionSuggestionEntity latest = latestSuggestion(tenantId, consultationId);
        String payloadJson = serializePayload(request, latest);
        OffsetDateTime generatedAt = request != null && request.generatedAt() != null ? request.generatedAt() : OffsetDateTime.now();
        if (latest != null
                && Objects.equals(latest.getGeneratedAt(), generatedAt)
                && Objects.equals(latest.getContentJson(), payloadJson)) {
            return toResponse(latest, currentSourceHash);
        }

        int versionNumber = latest == null ? 1 : latest.getVersionNumber() + 1;
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        String generatedByDisplayName = resolveCurrentDisplayName(tenantId, actorAppUserId);
        ConsultationAiPrescriptionSuggestionEntity saved = repository.save(ConsultationAiPrescriptionSuggestionEntity.create(
                tenantId,
                consultationId,
                versionNumber,
                currentSourceHash,
                normalize(request == null ? null : request.provider()),
                normalize(request == null ? null : request.model()),
                actorAppUserId,
                generatedByDisplayName,
                generatedAt,
                payloadJson
        ));
        if (latest != null) {
            latest.supersede(saved.getId());
            repository.save(latest);
        }
        audit(tenantId, consultationId, saved, latest);
        return toResponse(saved, currentSourceHash);
    }

    private ConsultationRecord requireConsultation(UUID tenantId, UUID consultationId) {
        return consultationService.findById(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found"));
    }

    private ConsultationAiPrescriptionSuggestionEntity latestSuggestion(UUID tenantId, UUID consultationId) {
        if (consultationId == null) {
            return null;
        }
        return repository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId).orElse(null);
    }

    private List<LabOrderRecord> currentLabOrders(UUID tenantId, UUID consultationId) {
        return labService.listOrders(tenantId, consultationId, null, null, null, null);
    }

    private String currentSourceHash(ConsultationRecord consultation, ClinicalContextResponse context, List<LabOrderRecord> labOrders) {
        return contextHasher.sourceHash(consultation, context, null, labOrders);
    }

    private String serializePayload(ConsultationAiPrescriptionSuggestionRequest request, ConsultationAiPrescriptionSuggestionEntity previous) {
        try {
            return objectMapper.writeValueAsString(buildPayload(request, previous));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize consultation AI prescription suggestion payload", ex);
        }
    }

    private SuggestionPayload buildPayload(ConsultationAiPrescriptionSuggestionRequest request, ConsultationAiPrescriptionSuggestionEntity previous) {
        Map<String, PreviousItem> previousItems = new LinkedHashMap<>();
        if (previous != null && StringUtils.hasText(previous.getContentJson())) {
            try {
                SuggestionPayload previousPayload = objectMapper.readValue(previous.getContentJson(), SuggestionPayload.class);
                if (previousPayload.items() != null) {
                    for (SuggestionPayloadItem item : previousPayload.items()) {
                        previousItems.put(normalizeKey(item.itemId()), new PreviousItem(
                                item.reviewedByAppUserId(),
                                item.reviewedByDisplayName(),
                                item.reviewedAt(),
                                item.status(),
                                item.draftText()
                        ));
                    }
                }
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Unable to parse existing consultation AI prescription suggestion payload", ex);
            }
        }

        List<SuggestionPayloadItem> items = new ArrayList<>();
        if (request != null && request.items() != null) {
            UUID actorAppUserId = RequestContextHolder.require().appUserId();
            String actorDisplayName = resolveCurrentDisplayName(RequestContextHolder.requireTenantId(), actorAppUserId);
            OffsetDateTime now = OffsetDateTime.now();
            for (ConsultationAiPrescriptionSuggestionRequest.Item item : request.items()) {
                String itemId = normalize(item.itemId());
                String medicine = normalize(item.medicine());
                if (!StringUtils.hasText(itemId) || !StringUtils.hasText(medicine)) {
                    continue;
                }
                String status = normalizeStatus(item.status());
                String draftText = normalize(firstNonBlank(item.draftText(), buildDraftText(item)));
                PreviousItem previousItem = previousItems.get(normalizeKey(itemId));
                String reviewedByAppUserId = previousItem != null ? previousItem.reviewedByAppUserId() : null;
                String reviewedByDisplayName = previousItem != null ? previousItem.reviewedByDisplayName() : null;
                OffsetDateTime reviewedAt = previousItem != null ? previousItem.reviewedAt() : null;
                boolean reviewStateChanged = previousItem == null
                        || !Objects.equals(previousItem.status(), status)
                        || !Objects.equals(previousItem.draftText(), draftText);
                if (!"PENDING".equals(status) && reviewStateChanged) {
                    reviewedByAppUserId = actorAppUserId.toString();
                    reviewedByDisplayName = actorDisplayName;
                    reviewedAt = now;
                }
                if ("PENDING".equals(status)) {
                    reviewedByAppUserId = null;
                    reviewedByDisplayName = null;
                    reviewedAt = null;
                }
                items.add(new SuggestionPayloadItem(
                        itemId,
                        medicine,
                        normalize(item.dose()),
                        normalize(item.frequency()),
                        normalize(item.duration()),
                        normalize(item.reason()),
                        normalize(item.safetyNote()),
                        draftText,
                        status,
                        reviewedByAppUserId,
                        reviewedByDisplayName,
                        reviewedAt
                ));
            }
        }

        return new SuggestionPayload(
                normalize(request == null ? null : request.summary()),
                normalize(request == null ? null : request.rawText()),
                request != null && request.unstructured(),
                normalize(request == null ? null : request.provider()),
                normalize(request == null ? null : request.model()),
                request != null && request.generatedAt() != null ? request.generatedAt() : OffsetDateTime.now(),
                items
        );
    }

    private String buildDraftText(ConsultationAiPrescriptionSuggestionRequest.Item item) {
        return String.join("\n",
                "Medicine: " + firstNonBlank(item.medicine(), ""),
                StringUtils.hasText(item.dose()) ? "Dose: " + item.dose().trim() : "",
                StringUtils.hasText(item.frequency()) ? "Frequency: " + item.frequency().trim() : "",
                StringUtils.hasText(item.duration()) ? "Duration: " + item.duration().trim() : "",
                StringUtils.hasText(item.reason()) ? "Reason: " + item.reason().trim() : "",
                StringUtils.hasText(item.safetyNote()) ? "Safety: " + item.safetyNote().trim() : ""
        ).trim();
    }

    private ConsultationAiPrescriptionSuggestionResponse toResponse(ConsultationAiPrescriptionSuggestionEntity entity, String currentSourceHash) {
        SuggestionPayload payload = deserializePayload(entity.getContentJson());
        return new ConsultationAiPrescriptionSuggestionResponse(
                entity.getId() == null ? null : entity.getId().toString(),
                entity.getConsultationId() == null ? null : entity.getConsultationId().toString(),
                entity.getVersionNumber(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getSourceHash(),
                currentSourceHash,
                entity.getSourceHash() != null && currentSourceHash != null && !entity.getSourceHash().equals(currentSourceHash),
                payload.summary(),
                payload.rawText(),
                payload.unstructured(),
                entity.getProvider(),
                entity.getModel(),
                entity.getGeneratedByAppUserId() == null ? null : entity.getGeneratedByAppUserId().toString(),
                resolveGeneratedByDisplayName(entity),
                entity.getGeneratedAt(),
                payload.items() == null ? List.of() : payload.items().stream().map(this::toResponseItem).toList(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private ConsultationAiPrescriptionSuggestionResponse emptyResponse(UUID consultationId, String currentSourceHash) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ConsultationAiPrescriptionSuggestionResponse(
                null,
                consultationId == null ? null : consultationId.toString(),
                0,
                null,
                null,
                currentSourceHash,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                now,
                List.of(),
                now,
                now
        );
    }

    private SuggestionPayload deserializePayload(String contentJson) {
        if (!StringUtils.hasText(contentJson)) {
            return new SuggestionPayload(null, null, false, null, null, null, List.of());
        }
        try {
            return objectMapper.readValue(contentJson, SuggestionPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize consultation AI prescription suggestion payload", ex);
        }
    }

    private ConsultationAiPrescriptionSuggestionResponse.Item toResponseItem(SuggestionPayloadItem item) {
        return new ConsultationAiPrescriptionSuggestionResponse.Item(
                item.itemId(),
                item.medicine(),
                item.dose(),
                item.frequency(),
                item.duration(),
                item.reason(),
                item.safetyNote(),
                item.draftText(),
                item.status(),
                item.reviewedByAppUserId(),
                item.reviewedByDisplayName(),
                item.reviewedAt()
        );
    }

    private String resolveGeneratedByDisplayName(ConsultationAiPrescriptionSuggestionEntity entity) {
        if (entity == null) {
            return "User unavailable";
        }
        if (StringUtils.hasText(entity.getGeneratedByDisplayName())) {
            return entity.getGeneratedByDisplayName();
        }
        if (entity.getGeneratedByAppUserId() == null) {
            return "User unavailable";
        }
        return resolveCurrentDisplayName(entity.getTenantId(), entity.getGeneratedByAppUserId());
    }

    private String resolveCurrentDisplayName(UUID tenantId, UUID appUserId) {
        if (appUserId == null) {
            return "User unavailable";
        }
        return tenantUserManagementService.list(tenantId).stream()
                .filter(record -> appUserId.equals(record.appUserId()))
                .map(TenantUserRecord::displayName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("User unavailable");
    }

    private void audit(UUID tenantId, UUID consultationId, ConsultationAiPrescriptionSuggestionEntity saved, ConsultationAiPrescriptionSuggestionEntity previous) {
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", saved.getId());
        details.put("tenantId", tenantId);
        details.put("consultationId", consultationId);
        details.put("versionNumber", saved.getVersionNumber());
        details.put("status", saved.getStatus());
        details.put("sourceHash", saved.getSourceHash());
        details.put("generatedByAppUserId", saved.getGeneratedByAppUserId());
        details.put("generatedByDisplayName", saved.getGeneratedByDisplayName());
        details.put("generatedAt", saved.getGeneratedAt());
        details.put("stale", false);
        details.put("supersededPreviousId", previous == null ? null : previous.getId());
        details.put("items", deserializePayload(saved.getContentJson()).items());
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                ENTITY_TYPE,
                saved.getId(),
                previous == null ? "consultation.ai_prescription_suggestion.created" : "consultation.ai_prescription_suggestion.updated",
                actorAppUserId,
                OffsetDateTime.now(),
                previous == null ? "Created prescription suggestion draft" : "Updated prescription suggestion draft",
                safeJson(details)
        ));
    }

    private String safeJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return "{\"id\":\"" + details.get("id") + "\"}";
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return "PENDING";
        }
        String upper = normalized.toUpperCase();
        return switch (upper) {
            case "ACCEPTED", "REJECTED", "EDITED", "PENDING" -> upper;
            default -> "PENDING";
        };
    }

    private String normalizeKey(String value) {
        return normalize(value) == null ? "" : normalize(value).toLowerCase();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : second;
    }

    private record PreviousItem(
            String reviewedByAppUserId,
            String reviewedByDisplayName,
            OffsetDateTime reviewedAt,
            String status,
            String draftText
    ) {}

    private record SuggestionPayload(
            String summary,
            String rawText,
            boolean unstructured,
            String provider,
            String model,
            OffsetDateTime generatedAt,
            List<SuggestionPayloadItem> items
    ) {}

    private record SuggestionPayloadItem(
            String itemId,
            String medicine,
            String dose,
            String frequency,
            String duration,
            String reason,
            String safetyNote,
            String draftText,
            String status,
            String reviewedByAppUserId,
            String reviewedByDisplayName,
            OffsetDateTime reviewedAt
    ) {}
}
