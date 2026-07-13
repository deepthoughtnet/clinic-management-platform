package com.deepthoughtnet.clinic.api.ai.reasoning;

import com.deepthoughtnet.clinic.api.ai.clinicalcontext.ClinicalContextService;
import com.deepthoughtnet.clinic.api.ai.dto.ClinicalContextResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningRequest;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResponse;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ClinicalReasoningResult;
import com.deepthoughtnet.clinic.api.ai.reasoning.dto.ReasoningMetadata;
import com.deepthoughtnet.clinic.consultation.db.ClinicalReasoningResultEntity;
import com.deepthoughtnet.clinic.consultation.db.ClinicalReasoningResultRepository;
import com.deepthoughtnet.clinic.consultation.db.ConsultationEntity;
import com.deepthoughtnet.clinic.consultation.db.ConsultationRepository;
import com.deepthoughtnet.clinic.identity.service.TenantUserManagementService;
import com.deepthoughtnet.clinic.identity.service.model.TenantUserRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClinicalReasoningService {
    private static final Logger log = LoggerFactory.getLogger(ClinicalReasoningService.class);
    private static final String STALE_REASON = "Clinical data changed after this reasoning was generated. Regenerate to refresh.";

    private final ConsultationRepository consultationRepository;
    private final ClinicalContextService clinicalContextService;
    private final ClinicalReasoningEngine clinicalReasoningEngine;
    private final ClinicalReasoningResultRepository reasoningResultRepository;
    private final ClinicalReasoningContextHasher contextHasher;
    private final TenantUserManagementService tenantUserManagementService;
    private final AuditEventPublisher auditEventPublisher;
    private final ObjectMapper objectMapper;

    public ClinicalReasoningService(ConsultationRepository consultationRepository,
                                    ClinicalContextService clinicalContextService,
                                    ClinicalReasoningEngine clinicalReasoningEngine,
                                    ClinicalReasoningResultRepository reasoningResultRepository,
                                    ClinicalReasoningContextHasher contextHasher,
                                    TenantUserManagementService tenantUserManagementService,
                                    AuditEventPublisher auditEventPublisher,
                                    ObjectMapper objectMapper) {
        this.consultationRepository = consultationRepository;
        this.clinicalContextService = clinicalContextService;
        this.clinicalReasoningEngine = clinicalReasoningEngine;
        this.reasoningResultRepository = reasoningResultRepository;
        this.contextHasher = contextHasher;
        this.tenantUserManagementService = tenantUserManagementService;
        this.auditEventPublisher = auditEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ClinicalReasoningResponse get(UUID tenantId, UUID consultationId) {
        ConsultationEntity consultation = requireConsultation(tenantId, consultationId);
        ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(tenantId, consultation.getPatientId(), consultationId);
        ClinicalReasoningResultEntity latest = latestReasoning(tenantId, consultationId);
        if (latest == null) {
            return buildResponse(consultation, clinicalContext, null, null, null, "NOT_GENERATED", null, null, null, null, false);
        }
        String currentContextHash = currentContextHash(tenantId, consultation, clinicalContext);
        boolean stale = !currentContextHash.equals(latest.getContextHash());
        String reasoningStatus = stale ? "STALE" : latest.getStatus().name();
        String staleReason = stale ? STALE_REASON : null;
        ClinicalReasoningResult result = deserializeResult(latest.getResultJson());
        return buildResponse(
                consultation,
                clinicalContext,
                result,
                latest.getId(),
                latest.getVersionNumber(),
                reasoningStatus,
                latest.getContextHash(),
                staleReason,
                latest.getGeneratedByAppUserId(),
                resolveGeneratedByDisplayName(tenantId, latest),
                false
        );
    }

    @Transactional
    public ClinicalReasoningResponse generate(UUID tenantId, UUID consultationId, ClinicalReasoningRequest request, boolean debug) {
        ConsultationEntity consultation = requireConsultation(tenantId, consultationId);
        if (request != null && request.patientId() != null && !request.patientId().equals(consultation.getPatientId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient mismatch for consultation");
        }
        ClinicalContextResponse clinicalContext = clinicalContextService.buildClinicalContext(tenantId, consultation.getPatientId(), consultationId);
        ClinicalReasoningRequest persistedRequest = buildPersistedRequest(consultation, clinicalContext);
        String correlationId = RequestContextHolder.require().correlationId();
        String requestId = correlationId;
        log.info("[AI-REASONING-TRACE] tenantId={} consultationId={} patientId={} requestId={} correlationId={} stage=CONTEXT_READY",
                tenantId, consultationId, consultation.getPatientId(), requestId, correlationId);
        ClinicalReasoningResult reasoningResult = clinicalReasoningEngine.generate(
                new ClinicalReasoningEngine.UUIDContext(tenantId, requestId, correlationId),
                consultation,
                persistedRequest,
                clinicalContext);
        if (!hasPersistableReasoning(reasoningResult)) {
            auditAttempt(tenantId, consultation, "clinical_reasoning.generate.failed", "Clinical reasoning generation failed", reasoningResult == null ? null : reasoningResult.metadata());
            return buildResponse(consultation, clinicalContext, null, null, null, "FAILED", null, null, null, null, debug);
        }
        ClinicalReasoningResultEntity persisted = persistReasoning(tenantId, consultation, clinicalContext, reasoningResult);
        auditAttempt(tenantId, consultation, "clinical_reasoning.generated", "Clinical reasoning generated", reasoningResult.metadata());
        return buildResponse(
                consultation,
                clinicalContext,
                reasoningResult,
                persisted.getId(),
                persisted.getVersionNumber(),
                persisted.getStatus().name(),
                persisted.getContextHash(),
                null,
                persisted.getGeneratedByAppUserId(),
                persisted.getGeneratedByDisplayName(),
                debug
        );
    }

    public ClinicalReasoningResponse generate(UUID tenantId, UUID consultationId, ClinicalReasoningRequest request) {
        return generate(tenantId, consultationId, request, false);
    }

    private ClinicalReasoningResultEntity persistReasoning(UUID tenantId,
                                                           ConsultationEntity consultation,
                                                           ClinicalContextResponse clinicalContext,
                                                           ClinicalReasoningResult reasoningResult) {
        String contextHash = currentContextHash(tenantId, consultation, clinicalContext);
        ClinicalReasoningResultEntity previous = latestReasoning(tenantId, consultation.getId());
        int versionNumber = previous == null ? 1 : previous.getVersionNumber() + 1;
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        String generatedByDisplayName = resolveCurrentDisplayName(tenantId, actorAppUserId);
        ClinicalReasoningResultEntity persisted = reasoningResultRepository.save(ClinicalReasoningResultEntity.create(
                tenantId,
                consultation.getId(),
                consultation.getPatientId(),
                versionNumber,
                contextHash,
                ClinicalReasoningPromptBuilder.PROMPT_VERSION,
                ClinicalReasoningPromptBuilder.REASONING_ENGINE_VERSION,
                reasoningResult.provider(),
                reasoningResult.model(),
                actorAppUserId,
                generatedByDisplayName,
                reasoningResult.generatedAt() == null ? OffsetDateTime.now() : reasoningResult.generatedAt(),
                serialize(reasoningResult)
        ));
        if (previous != null) {
            previous.supersede(persisted.getId());
            reasoningResultRepository.save(previous);
        }
        return persisted;
    }

    private ClinicalReasoningResponse buildResponse(ConsultationEntity consultation,
                                                    ClinicalContextResponse clinicalContext,
                                                    ClinicalReasoningResult reasoningResult,
                                                    UUID reasoningId,
                                                    Integer versionNumber,
                                                    String reasoningStatus,
                                                    String contextHash,
                                                    String staleReason,
                                                    UUID generatedByAppUserId,
                                                    String generatedByDisplayName,
                                                    boolean debug) {
        String consultationVitals = buildConsultationVitals(consultation, clinicalContext);
        String vitalsSource = determineVitalsSource(consultation, clinicalContext);
        ClinicalReasoningResponse.ConsultationSummary consultationSummary = new ClinicalReasoningResponse.ConsultationSummary(
                consultation.getId(),
                consultation.getPatientId(),
                consultation.getStatus() == null ? null : consultation.getStatus().name(),
                consultation.getChiefComplaints(),
                consultation.getSymptoms(),
                consultation.getDiagnosis(),
                consultation.getAdvice(),
                consultation.getClinicalNotes(),
                consultationVitals,
                vitalsSource,
                vitalsSource == null ? null : ("INTAKE".equals(vitalsSource) ? "Latest completed intake" : "Doctor-entered consultation vitals")
        );
        ClinicalReasoningResponse.ClinicalContextSummary clinicalContextSummary = ClinicalReasoningResponse.ClinicalContextSummary.from(clinicalContext, consultationVitals, vitalsSource);
        ReasoningMetadata metadata = reasoningResult == null ? null : reasoningResult.metadata();
        Map<String, Object> debugPayload = null;
        if (debug) {
            debugPayload = new LinkedHashMap<>();
            debugPayload.put("clinicalContext", clinicalContext);
            debugPayload.put("clinicalContextJson", clinicalContext == null ? null : clinicalContext.clinicalContextJson());
            debugPayload.put("aiPromptContext", clinicalContext == null ? null : clinicalContext.aiPromptContext());
            debugPayload.put("aiSummary", clinicalContext == null ? null : clinicalContext.aiSummary());
        }
        return new ClinicalReasoningResponse(
                consultationSummary,
                clinicalContextSummary,
                reasoningResult,
                metadata,
                debugPayload,
                reasoningId == null ? null : reasoningId.toString(),
                versionNumber,
                reasoningStatus,
                contextHash,
                staleReason,
                generatedByAppUserId,
                generatedByDisplayName
        );
    }

    private ClinicalReasoningRequest buildPersistedRequest(ConsultationEntity consultation, ClinicalContextResponse context) {
        return new ClinicalReasoningRequest(
                consultation.getPatientId(),
                consultation.getChiefComplaints(),
                consultation.getSymptoms(),
                consultation.getClinicalNotes(),
                consultationVitals(consultation, context),
                consultation.getDiagnosis(),
                consultation.getAdvice(),
                consultation.getClinicalNotes(),
                null,
                null
        );
    }

    private String consultationVitals(ConsultationEntity consultation, ClinicalContextResponse clinicalContext) {
        String consultationVitals = buildVitalsFromConsultation(consultation);
        if (consultationVitals != null) {
            return consultationVitals;
        }
        return buildVitalsFromIntake(clinicalContext);
    }

    private ConsultationEntity requireConsultation(UUID tenantId, UUID consultationId) {
        return consultationRepository.findByTenantIdAndId(tenantId, consultationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation not found"));
    }

    private ClinicalReasoningResultEntity latestReasoning(UUID tenantId, UUID consultationId) {
        if (consultationId == null) {
            return null;
        }
        return reasoningResultRepository.findFirstByTenantIdAndConsultationIdOrderByVersionNumberDesc(tenantId, consultationId).orElse(null);
    }

    private String currentContextHash(UUID tenantId, ConsultationEntity consultation, ClinicalContextResponse clinicalContext) {
        return contextHasher.contextHash(tenantId, consultation, clinicalContext, buildPersistedRequest(consultation, clinicalContext));
    }

    private String resolveGeneratedByDisplayName(UUID tenantId, ClinicalReasoningResultEntity entity) {
        if (entity == null) {
            return "User unavailable";
        }
        if (StringUtils.hasText(entity.getGeneratedByDisplayName())) {
            return entity.getGeneratedByDisplayName();
        }
        if (entity.getGeneratedByAppUserId() == null) {
            return "User unavailable";
        }
        return resolveCurrentDisplayName(tenantId, entity.getGeneratedByAppUserId());
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

    private boolean hasPersistableReasoning(ClinicalReasoningResult result) {
        return result != null && (
                StringUtils.hasText(result.reasoningSummary())
                        || (result.primaryDiagnosis() != null && StringUtils.hasText(result.primaryDiagnosis().name()))
                        || (result.differentialDiagnoses() != null && !result.differentialDiagnoses().isEmpty())
                        || (result.supportingEvidence() != null && !result.supportingEvidence().isEmpty())
                        || (result.contradictingEvidence() != null && !result.contradictingEvidence().isEmpty())
                        || (result.missingInformation() != null && !result.missingInformation().isEmpty())
                        || (result.redFlags() != null && !result.redFlags().isEmpty())
                        || (result.recommendedTests() != null && !result.recommendedTests().isEmpty())
                        || (result.safetyNotes() != null && !result.safetyNotes().isEmpty())
        );
    }

    private String serialize(ClinicalReasoningResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize clinical reasoning result", ex);
        }
    }

    private ClinicalReasoningResult deserializeResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ClinicalReasoningResult.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize clinical reasoning result", ex);
        }
    }

    private void auditAttempt(UUID tenantId, ConsultationEntity consultation, String action, String summary, ReasoningMetadata metadata) {
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("promptVersion", metadata == null ? ClinicalReasoningPromptBuilder.PROMPT_VERSION : metadata.promptVersion());
        payload.put("contextVersion", metadata == null ? ClinicalReasoningPromptBuilder.CONTEXT_VERSION : metadata.contextVersion());
        payload.put("provider", metadata == null ? null : metadata.provider());
        payload.put("model", metadata == null ? null : metadata.model());
        payload.put("parseStatus", metadata == null ? null : metadata.parseStatus());
        payload.put("resultQuality", metadata == null ? null : metadata.resultQuality());
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                "CLINICAL_REASONING",
                consultation.getId(),
                action,
                actorAppUserId,
                OffsetDateTime.now(),
                summary,
                serialize(payload)
        ));
    }

    private String serialize(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize audit payload", ex);
        }
    }

    private String determineVitalsSource(ConsultationEntity consultation, ClinicalContextResponse clinicalContext) {
        boolean consultationVitalsPresent = consultation != null
                && (consultation.getBloodPressureSystolic() != null
                || consultation.getBloodPressureDiastolic() != null
                || consultation.getPulseRate() != null
                || consultation.getTemperature() != null
                || consultation.getSpo2() != null
                || consultation.getRespiratoryRate() != null
                || consultation.getWeightKg() != null
                || consultation.getHeightCm() != null);
        if (consultationVitalsPresent) {
            return "CONSULTATION";
        }
        return clinicalContext != null && clinicalContext.intakeSummary() != null && clinicalContext.intakeSummary().latestVitals() != null ? "INTAKE" : null;
    }

    private String buildConsultationVitals(ConsultationEntity consultation, ClinicalContextResponse clinicalContext) {
        String consultationVitals = buildVitalsFromConsultation(consultation);
        if (consultationVitals != null) {
            return consultationVitals;
        }
        return buildVitalsFromIntake(clinicalContext);
    }

    private String buildVitalsFromConsultation(ConsultationEntity consultation) {
        if (consultation == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        appendVital(builder, consultation.getBloodPressureSystolic() == null || consultation.getBloodPressureDiastolic() == null ? null : "BP " + consultation.getBloodPressureSystolic() + "/" + consultation.getBloodPressureDiastolic());
        appendVital(builder, consultation.getPulseRate() == null ? null : "Pulse " + consultation.getPulseRate());
        appendVital(builder, consultation.getTemperature() == null ? null : "Temp " + consultation.getTemperature() + (consultation.getTemperatureUnit() == null ? "" : " " + consultation.getTemperatureUnit().name()));
        appendVital(builder, consultation.getSpo2() == null ? null : "SpO2 " + consultation.getSpo2());
        appendVital(builder, consultation.getRespiratoryRate() == null ? null : "RR " + consultation.getRespiratoryRate());
        appendVital(builder, consultation.getWeightKg() == null ? null : "Weight " + consultation.getWeightKg());
        appendVital(builder, consultation.getHeightCm() == null ? null : "Height " + consultation.getHeightCm());
        return builder.length() == 0 ? null : builder.toString();
    }

    private String buildVitalsFromIntake(ClinicalContextResponse clinicalContext) {
        if (clinicalContext == null || clinicalContext.intakeSummary() == null || clinicalContext.intakeSummary().latestVitals() == null) {
            return null;
        }
        ClinicalContextResponse.VitalsSnapshot vitals = clinicalContext.intakeSummary().latestVitals();
        StringBuilder builder = new StringBuilder();
        appendVital(builder, vitals.bloodPressureSystolic() == null || vitals.bloodPressureDiastolic() == null ? null : "BP " + vitals.bloodPressureSystolic() + "/" + vitals.bloodPressureDiastolic());
        appendVital(builder, vitals.pulseRate() == null ? null : "Pulse " + vitals.pulseRate());
        appendVital(builder, vitals.temperature() == null ? null : "Temp " + vitals.temperature() + (vitals.temperatureUnit() == null ? "" : " " + vitals.temperatureUnit()));
        appendVital(builder, vitals.spo2() == null ? null : "SpO2 " + vitals.spo2());
        appendVital(builder, vitals.respiratoryRate() == null ? null : "RR " + vitals.respiratoryRate());
        appendVital(builder, vitals.randomBloodSugar() == null ? null : "RBS " + vitals.randomBloodSugar());
        appendVital(builder, vitals.bmi() == null ? null : "BMI " + String.format(java.util.Locale.ROOT, "%.1f", vitals.bmi()));
        return builder.length() == 0 ? null : "INTAKE " + builder;
    }

    private void appendVital(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(value);
    }
}
