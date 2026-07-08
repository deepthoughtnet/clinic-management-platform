package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.service.AiDoctorCopilotService;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobType;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentTextExtractionResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.ai.orchestration.service.AgentExecutionLogService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.TenantNotificationSettingsService;
import com.deepthoughtnet.clinic.carepilot.notificationsettings.service.model.NotificationSettingsRecord;
import com.deepthoughtnet.clinic.api.clinicalmemory.service.PatientLongitudinalMemoryService;
import com.deepthoughtnet.clinic.identity.db.AppUserEntity;
import com.deepthoughtnet.clinic.identity.db.AppUserRepository;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.CorrelationId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalDocumentAiExtractionService {
    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentAiExtractionService.class);
    private static final String ENTITY_TYPE = "CLINICAL_DOCUMENT_AI";
    private static final String FRIENDLY_AI_FAILURE_MESSAGE = "AI processing could not complete. Please retry.";
    private static final Pattern LAB_PATTERN = Pattern.compile(
            "(?i)\\b(hemoglobin|hb|glucose|blood sugar|cholesterol|hdl|ldl|triglycerides|bilirubin|alt|ast|alp|alk phos|creatinine)\\b[^\\d\\n]{0,20}([<>]?\\s*\\d+(?:\\.\\d+)?)"
    );

    private final ClinicalAiJobRepository jobRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final ClinicalDocumentService documentService;
    private final PatientLongitudinalMemoryService longitudinalMemoryService;
    private final AppUserRepository appUserRepository;
    private final ClinicalDocumentTextExtractionService textExtractionService;
    private final AiDoctorCopilotService aiDoctorCopilotService;
    private final ObjectStorageService storageService;
    private final AuditEventPublisher auditEventPublisher;
    private final AgentExecutionLogService agentExecutionLogService;
    private final PatientService patientService;
    private final TenantNotificationSettingsService notificationSettingsService;
    private final ObjectMapper objectMapper;
    private final long retryBackoffMs;
    private final int maxAttempts;

    public ClinicalDocumentAiExtractionService(ClinicalAiJobRepository jobRepository,
                                               ClinicalDocumentRepository documentRepository,
                                               ClinicalDocumentService documentService,
                                               PatientLongitudinalMemoryService longitudinalMemoryService,
                                               AppUserRepository appUserRepository,
                                               ClinicalDocumentTextExtractionService textExtractionService,
                                               AiDoctorCopilotService aiDoctorCopilotService,
                                               ObjectStorageService storageService,
                                               AuditEventPublisher auditEventPublisher,
                                               AgentExecutionLogService agentExecutionLogService,
                                               PatientService patientService,
                                               TenantNotificationSettingsService notificationSettingsService,
                                               ObjectMapper objectMapper,
                                               @Value("${clinic.ai.jobs.retryBackoffMs:60000}") long retryBackoffMs,
                                               @Value("${clinic.ai.jobs.maxAttempts:3}") int maxAttempts) {
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.longitudinalMemoryService = longitudinalMemoryService;
        this.appUserRepository = appUserRepository;
        this.textExtractionService = textExtractionService;
        this.aiDoctorCopilotService = aiDoctorCopilotService;
        this.storageService = storageService;
        this.auditEventPublisher = auditEventPublisher;
        this.agentExecutionLogService = agentExecutionLogService;
        this.patientService = patientService;
        this.notificationSettingsService = notificationSettingsService;
        this.objectMapper = objectMapper;
        this.retryBackoffMs = retryBackoffMs <= 0 ? 60000 : retryBackoffMs;
        this.maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    }

    @Transactional
    public ClinicalAiJobEntity queueExtraction(UUID tenantId, UUID documentId, UUID actorAppUserId) {
        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return enqueueExtraction(document, actorAppUserId, false, "clinical.document.ai_extraction.queued");
    }

    @Transactional
    public ClinicalAiJobEntity reprocessExtraction(UUID tenantId, UUID documentId, UUID actorAppUserId) {
        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        return enqueueExtraction(document, actorAppUserId, true, "clinical.document.ai_extraction.reprocess_requested");
    }

    @Transactional
    public int processPendingJobs() {
        int processed = 0;
        for (ClinicalAiJobEntity job : jobRepository.findTop25ByStatusInOrderByCreatedAtAsc(
                List.of(ClinicalAiJobStatus.QUEUED, ClinicalAiJobStatus.RETRY_SCHEDULED))) {
            if (job.getNextAttemptAt() != null && job.getNextAttemptAt().isAfter(OffsetDateTime.now())) {
                continue;
            }
            process(job.getId());
            processed++;
        }
        return processed;
    }

    @Transactional
    public void process(UUID jobId) {
        ClinicalAiJobEntity job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("AI job not found"));
        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(job.getTenantId(), job.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        ProcessingRequestContext context = ProcessingRequestContext.fromJob(job, objectMapper);

        job.markProcessing();
        document.markAiExtractionProcessing("PROCESSING");
        documentRepository.save(document);
        jobRepository.save(job);

        RequestContext previousContext = RequestContextHolder.get();
        String previousCorrelationId = org.slf4j.MDC.get(CorrelationId.MDC_KEY);
        try {
            restoreRequestContext(context, job, document);
            log.info("Clinical document AI processing started. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=AI_PROCESSING_STARTED",
                    job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId());
            byte[] bytes = storageService.getObjectBytes(document.getStorageKey());
            ClinicalDocumentTextExtractionResult textResult = textExtractionService.extract(document, bytes);
            if ("FAILED".equalsIgnoreCase(textResult.status())) {
                log.warn("Clinical document OCR failed. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=OCR_FAILED, ocrProvider={}",
                        job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId(), textResult.provider());
            } else {
                log.info("Clinical document OCR completed. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=OCR_COMPLETED, ocrStatus={}, ocrProvider={}",
                        job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId(), textResult.status(), textResult.provider());
            }
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("documentId", document.getId().toString());
            input.put("documentType", document.getDocumentType().name());
            input.put("originalFilename", document.getOriginalFilename());
            input.put("notes", document.getNotes());
            input.put("referredDoctor", document.getReferredDoctor());
            input.put("referredHospital", document.getReferredHospital());
            input.put("referralNotes", document.getReferralNotes());
            input.put("ocrProvider", textResult.provider());
            input.put("ocrStatus", textResult.status());
            input.put("ocrText", textResult.text());
            input.put("possibleAbnormalFindings", abnormalFindings(textResult.text()));
            input.put("patient", patientSummary(document.getTenantId(), document.getPatientId()));

            List<AiEvidenceReference> evidence = List.of(new AiEvidenceReference(
                    AiProductCode.CLINIC,
                    document.getTenantId(),
                    "PATIENT_CLINICAL_DOCUMENT",
                    document.getId(),
                    document.getOriginalFilename(),
                    textResult.text() == null ? "" : textResult.text().substring(0, Math.min(500, textResult.text().length())),
                    Map.of("documentType", document.getDocumentType().name())
            ));

            AiDraftResponse response = aiDoctorCopilotService.draft(
                    AiTaskType.CLINICAL_DOCUMENT_EXTRACTION,
                    "clinic.clinical.document-extraction.v1",
                    "clinical_document_extraction",
                    input,
                    evidence
            );

            Map<String, Object> structuredData = new LinkedHashMap<>();
            if (response.structuredData() != null) {
                structuredData.putAll(response.structuredData());
            }
            structuredData.putIfAbsent("documentType", document.getDocumentType().name());
            structuredData.putIfAbsent("possibleAbnormalFindings", abnormalFindings(textResult.text()));
            structuredData.putIfAbsent("ocrProvider", textResult.provider());
            structuredData.putIfAbsent("ocrStatus", textResult.status());
            traceStructuredData(job, document, context, structuredData);
            String resultJson = toJson(structuredData);
            String summary = response.draft();
            BigDecimal confidence = response.confidence();
            String reviewStatus = response.enabled() ? "REVIEW_REQUIRED" : "DISABLED";

            document.markAiExtractionSucceeded(
                    response.provider(),
                    response.model(),
                    confidence,
                    summary,
                    resultJson,
                    reviewStatus,
                    textResult.provider()
            );
            documentRepository.save(document);

            if (response.enabled()) {
                job.markReviewRequired(response.provider(), response.model(), textResult.provider(), confidence, summary, resultJson);
            } else {
                job.markSucceeded(response.provider(), response.model(), textResult.provider(), confidence, summary, resultJson, reviewStatus, null, null, null);
            }
            jobRepository.save(job);
            try {
                longitudinalMemoryService.ingestPendingConcepts(document, resultJson, textResult.text(), confidence, summary);
            } catch (RuntimeException persistenceEx) {
                log.warn("Clinical document longitudinal concept persistence failed. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=LONGITUDINAL_PERSISTENCE_FAILED, error={}",
                        job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId(),
                        persistenceEx.getMessage() == null || persistenceEx.getMessage().isBlank() ? persistenceEx.getClass().getSimpleName() : persistenceEx.getMessage(),
                        persistenceEx);
            }

            agentExecutionLogService.record(
                    document.getTenantId(),
                    "CLINICAL_DOCUMENT_EXTRACTION",
                    document.getId(),
                    resultJson,
                    reviewStatus,
                    RequestContextHolder.get() == null ? null : RequestContextHolder.require().appUserId()
            );

            auditEventPublisher.record(new AuditEventCommand(
                    document.getTenantId(),
                    AuditEntityType.DOCUMENT,
                    document.getId(),
                    "clinical.document.ai_extraction.completed",
                    job.getRequestedByAppUserId(),
                    OffsetDateTime.now(),
                    "Completed clinical AI extraction",
                    resultJson
            ));
            log.info("Clinical document AI extraction completed. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=AI_EXTRACTION_COMPLETED, reviewStatus={}",
                    job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId(), reviewStatus);
        } catch (RuntimeException ex) {
            boolean retryable = job.getAttemptCount() + 1 < maxAttempts;
            String technicalMessage = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
            document.markAiExtractionFailed(job.getProvider(), job.getModel(), FRIENDLY_AI_FAILURE_MESSAGE);
            documentRepository.save(document);
            job.markFailed(technicalMessage, retryable, retryBackoffMs);
            jobRepository.save(job);
            if (isExpectedMissingContextFailure(ex)) {
                log.warn("Clinical AI extraction failed due to missing worker context. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=REQUEST_CONTEXT_RESTORE, retryable={}, error={}",
                        job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId(), retryable, technicalMessage);
            } else {
                log.warn("Clinical AI extraction failed safely. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=AI_EXTRACTION_FAILED, retryable={}, error={}",
                        job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId(), retryable, technicalMessage, ex);
            }
            if ("FAILED".equalsIgnoreCase(document.getOcrStatus())) {
                log.warn("Clinical document OCR failed. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=OCR_FAILED",
                        job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId());
            }
        } finally {
            RequestContextHolder.clear();
            if (previousContext != null) {
                RequestContextHolder.set(previousContext);
            }
            if (previousCorrelationId == null || previousCorrelationId.isBlank()) {
                org.slf4j.MDC.remove(CorrelationId.MDC_KEY);
            } else {
                org.slf4j.MDC.put(CorrelationId.MDC_KEY, previousCorrelationId);
            }
        }
    }

    @Transactional
    public ClinicalDocumentRecord review(UUID tenantId,
                                        UUID documentId,
                                        UUID reviewerAppUserId,
                                        boolean approved,
                                        boolean saveToPatientHistory,
                                        String reviewNotes,
                                        String acceptedStructuredJson,
                                        String overrideReason,
                                        String editedSummary) {
        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String reviewStatus = approved ? "APPROVED" : "REJECTED";
        String acceptedJson = approved ? normalizeJson(acceptedStructuredJson, document.getAiExtractionStructuredJson()) : null;
        String effectiveReviewNotes = reviewNotes == null || reviewNotes.isBlank() ? editedSummary : reviewNotes;
        document.markAiExtractionReviewed(reviewerAppUserId, effectiveReviewNotes, reviewStatus, acceptedJson, overrideReason);
        ClinicalDocumentEntity saved = documentRepository.save(document);
        longitudinalMemoryService.verifyDocumentConcepts(saved, approved, reviewerAppUserId, acceptedJson, effectiveReviewNotes, overrideReason);

        jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(tenantId, documentId, ClinicalAiJobType.DOCUMENT_EXTRACTION)
                .ifPresent(job -> {
                    job.markReviewed(reviewerAppUserId, approved, effectiveReviewNotes, reviewStatus,
                            approved ? reviewerAppUserId : null);
                    jobRepository.save(job);
                });

        if (approved && saveToPatientHistory) {
            pushToPatientHistory(saved, effectiveReviewNotes, reviewerAppUserId);
        }

        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                documentId,
                "clinical.document.ai_extraction.reviewed",
                reviewerAppUserId,
                OffsetDateTime.now(),
                "Reviewed clinical AI extraction",
                toJson(reviewAuditPayload(approved, reviewStatus, overrideReason, acceptedJson))
        ));
        return documentService.get(tenantId, documentId);
    }

    private void pushToPatientHistory(ClinicalDocumentEntity document, String reviewNotes, UUID reviewerAppUserId) {
        PatientRecord patient = patientService.findById(document.getTenantId(), document.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        String appended = appendPatientNote(patient.notes(), document, reviewNotes, reviewerAppUserId);
        String actorRole = RequestContextHolder.get() == null ? null : RequestContextHolder.get().tenantRole();
        ZoneId tenantZone = resolveTenantZone(document.getTenantId());
        String actorEmail = resolveActorEmail(document.getTenantId(), reviewerAppUserId);
        patientService.update(document.getTenantId(), patient.id(), new com.deepthoughtnet.clinic.patient.service.model.PatientUpsertCommand(
                patient.firstName(),
                patient.lastName(),
                patient.gender(),
                patient.dateOfBirth(),
                patient.ageYears(),
                patient.mobile(),
                patient.email(),
                patient.addressLine1(),
                patient.addressLine2(),
                patient.city(),
                patient.state(),
                patient.country(),
                patient.postalCode(),
                patient.emergencyContactName(),
                patient.emergencyContactMobile(),
                patient.bloodGroup(),
                patient.allergies(),
                patient.existingConditions(),
                patient.longTermMedications(),
                patient.surgicalHistory(),
                appended,
                patient.active()
        ), reviewerAppUserId, actorRole, tenantZone, actorEmail);
    }

    private ZoneId resolveTenantZone(UUID tenantId) {
        NotificationSettingsRecord settings = notificationSettingsService.findByTenantId(tenantId).orElse(null);
        if (settings == null || settings.timezone() == null || settings.timezone().isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(settings.timezone().trim());
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    private String resolveActorEmail(UUID tenantId, UUID actorAppUserId) {
        return null;
    }

    private String appendPatientNote(String existing, ClinicalDocumentEntity document, String reviewNotes, UUID reviewerAppUserId) {
        StringBuilder builder = new StringBuilder(existing == null ? "" : existing.trim());
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("[AI review ").append(OffsetDateTime.now()).append("] ");
        builder.append(document.getDocumentType().name().replace('_', ' '));
        builder.append(" - ");
        builder.append(document.getOriginalFilename());
        if (reviewNotes != null && !reviewNotes.isBlank()) {
            builder.append(" :: ").append(reviewNotes.trim());
        }
        builder.append(" (reviewed by ").append(reviewerAppUserId).append(")");
        return builder.toString();
    }

    private List<String> abnormalFindings(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher matcher = LAB_PATTERN.matcher(text);
        List<String> findings = new ArrayList<>();
        while (matcher.find() && findings.size() < 8) {
            findings.add("Possible abnormal finding detected: " + matcher.group(1) + " " + matcher.group(2));
        }
        return findings;
    }

    private String normalizeJson(String acceptedStructuredJson, String fallbackJson) {
        if (acceptedStructuredJson != null && !acceptedStructuredJson.isBlank()) {
            return acceptedStructuredJson.trim();
        }
        return fallbackJson;
    }

    private Map<String, Object> reviewAuditPayload(boolean approved, String reviewStatus, String overrideReason, String acceptedJson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approved", approved);
        payload.put("reviewStatus", reviewStatus);
        payload.put("overrideReason", overrideReason);
        payload.put("hasAcceptedStructuredJson", acceptedJson != null && !acceptedJson.isBlank());
        return payload;
    }

    private Map<String, Object> patientSummary(UUID tenantId, UUID patientId) {
        if (tenantId == null || patientId == null) {
            log.warn("Skipping patient summary for AI extraction due to missing tenantId/patientId. tenantId={}, patientId={}",
                    tenantId, patientId);
            return Map.of();
        }
        PatientRecord patient = patientService.findById(tenantId, patientId).orElse(null);
        if (patient == null) {
            log.warn("Skipping patient summary for AI extraction because patient was not found. tenantId={}, patientId={}",
                    tenantId, patientId);
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("patientId", safeString(patient.id()));
        summary.put("patientNumber", safeString(patient.patientNumber()));
        summary.put("patientName", safeString(patient.fullName()));
        summary.put("ageYears", patient.ageYears() == null ? "" : patient.ageYears());
        summary.put("gender", patient.gender() == null ? "" : patient.gender().name());
        summary.put("mobile", safeString(patient.mobile()));
        summary.put("allergies", safeString(patient.allergies()));
        summary.put("existingConditions", safeString(patient.existingConditions()));
        summary.put("longTermMedications", safeString(patient.longTermMedications()));
        summary.put("surgicalHistory", safeString(patient.surgicalHistory()));
        return summary;
    }

    private String safeString(Object value) {
        return value == null ? "" : value.toString();
    }

    private void traceStructuredData(ClinicalAiJobEntity job,
                                     ClinicalDocumentEntity document,
                                     ProcessingRequestContext context,
                                     Map<String, Object> structuredData) {
        if (!log.isInfoEnabled()) {
            return;
        }
        log.info(
                "[JEEV-LONG-MEM-TRACE] parsed-extraction tenantId={} patientId={} consultationId={} documentId={} extractedJsonKeys={} extractedLabCount={} extractedConditionCount={} extractedRiskFlagCount={}",
                job.getTenantId(),
                job.getPatientId(),
                document.getConsultationId(),
                document.getId(),
                structuredData == null ? List.of() : new ArrayList<>(structuredData.keySet()),
                countTraceItems(structuredData, "labs", "lab", "labResults", "lab_results", "results"),
                countTraceItems(structuredData, "conditions", "condition", "knownConditions", "known_conditions", "diagnoses"),
                countTraceItems(structuredData, "riskFlags", "risk_flag", "riskFlags", "risks", "flags")
        );
    }

    private int countTraceItems(Map<String, Object> structuredData, String... keys) {
        if (structuredData == null || keys == null) {
            return 0;
        }
        int total = 0;
        for (String key : keys) {
            Object value = structuredData.get(key);
            total += countTraceItems(value);
        }
        return total;
    }

    private int countTraceItems(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Map<?, ?> map) {
            int total = 0;
            for (Object entryValue : map.values()) {
                total += countTraceItems(entryValue);
            }
            return total;
        }
        if (value instanceof Iterable<?> iterable) {
            int total = 0;
            for (Object item : iterable) {
                total += countTraceItems(item);
            }
            return total;
        }
        if (value instanceof String text) {
            return text.isBlank() ? 0 : 1;
        }
        return 1;
    }

    private void restoreRequestContext(ProcessingRequestContext context, ClinicalAiJobEntity job, ClinicalDocumentEntity document) {
        if (context.tenantId() == null) {
            throw new IllegalStateException("Missing tenant context for clinical document AI job");
        }
        RequestContextHolder.set(new RequestContext(
                TenantId.of(context.tenantId()),
                context.actorAppUserId(),
                null,
                java.util.Set.of(),
                context.tenantRole(),
                context.correlationId()
        ));
        org.slf4j.MDC.put(CorrelationId.MDC_KEY, context.correlationId());
        log.info("Clinical document tenant context restored. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=REQUEST_CONTEXT_RESTORED, actorAppUserId={}, actorUsername={}",
                job.getId(), document.getTenantId(), document.getId(), document.getPatientId(), context.correlationId(), context.actorAppUserId(), context.actorUsername());
    }

    private String resolveActorUsername(UUID tenantId, UUID actorAppUserId) {
        if (tenantId == null || actorAppUserId == null) {
            return null;
        }
        return appUserRepository.findByTenantIdAndId(tenantId, actorAppUserId)
                .map(this::displayActorName)
                .orElse(null);
    }

    private String displayActorName(AppUserEntity user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().trim();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim();
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName().trim();
        }
        return null;
    }

    private boolean isExpectedMissingContextFailure(RuntimeException ex) {
        return ex instanceof IllegalStateException
                && ex.getMessage() != null
                && ex.getMessage().contains("Missing RequestContext");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize clinical AI payload", ex);
        }
    }

    private ClinicalAiJobEntity enqueueExtraction(ClinicalDocumentEntity document,
                                                  UUID actorAppUserId,
                                                  boolean forceNewJob,
                                                  String auditAction) {
        UUID tenantId = document.getTenantId();
        UUID documentId = document.getId();
        RequestContext requestContext = RequestContextHolder.get();
        Optional<ClinicalAiJobEntity> existing = forceNewJob
                ? Optional.empty()
                : jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(
                        tenantId, documentId, ClinicalAiJobType.DOCUMENT_EXTRACTION);
        if (existing.isPresent() && (existing.get().getStatus() == ClinicalAiJobStatus.QUEUED
                || existing.get().getStatus() == ClinicalAiJobStatus.PROCESSING)) {
            return existing.get();
        }
        ProcessingRequestContext processingContext = ProcessingRequestContext.capture(
                tenantId,
                document.getPatientId(),
                documentId,
                actorAppUserId,
                requestContext,
                resolveActorUsername(tenantId, actorAppUserId)
        );
        ClinicalAiJobEntity job = ClinicalAiJobEntity.queued(
                tenantId,
                ClinicalAiJobType.DOCUMENT_EXTRACTION,
                "PATIENT_CLINICAL_DOCUMENT",
                documentId,
                documentId,
                document.getPatientId(),
                document.getConsultationId(),
                actorAppUserId,
                toJson(Map.of(
                        "context", processingContext.asMap(),
                        "documentId", document.getId(),
                        "patientId", document.getPatientId(),
                        "documentType", document.getDocumentType().name(),
                        "reprocess", forceNewJob
                ))
        );
        ClinicalAiJobEntity saved = jobRepository.save(job);
        document.markAiExtractionQueued("NOT_STARTED");
        documentRepository.save(document);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                documentId,
                auditAction,
                actorAppUserId,
                OffsetDateTime.now(),
                forceNewJob ? "Queued document AI reprocess" : "Queued clinical AI extraction job",
                "{\"documentId\":\"%s\",\"reprocess\":%s}".formatted(documentId, forceNewJob)
        ));
        return saved;
    }

    private record ProcessingRequestContext(
            UUID tenantId,
            String tenantCode,
            UUID clinicId,
            UUID actorAppUserId,
            String actorUsername,
            String tenantRole,
            String correlationId,
            String requestId,
            UUID patientId,
            UUID documentId
    ) {
        private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

        static ProcessingRequestContext capture(UUID tenantId,
                                                UUID patientId,
                                                UUID documentId,
                                                UUID actorAppUserId,
                                                RequestContext requestContext,
                                                String actorUsername) {
            String correlationId = requestContext == null || requestContext.correlationId() == null || requestContext.correlationId().isBlank()
                    ? UUID.randomUUID().toString()
                    : requestContext.correlationId();
            return new ProcessingRequestContext(
                    tenantId,
                    null,
                    null,
                    actorAppUserId,
                    actorUsername,
                    requestContext == null ? null : requestContext.tenantRole(),
                    correlationId,
                    correlationId,
                    patientId,
                    documentId
            );
        }

        static ProcessingRequestContext fromJob(ClinicalAiJobEntity job, ObjectMapper objectMapper) {
            try {
                Map<String, Object> root = objectMapper.readValue(job.getRequestJson(), MAP_TYPE);
                Object raw = root.get("context");
                if (!(raw instanceof Map<?, ?> rawContext)) {
                    return fallback(job);
                }
                return new ProcessingRequestContext(
                        uuidValue(rawContext.get("tenantId"), job.getTenantId()),
                        stringValue(rawContext.get("tenantCode")),
                        uuidValue(rawContext.get("clinicId"), null),
                        uuidValue(rawContext.get("actorAppUserId"), job.getRequestedByAppUserId()),
                        stringValue(rawContext.get("actorUsername")),
                        stringValue(rawContext.get("tenantRole")),
                        CorrelationId.ensure(stringValue(rawContext.get("correlationId"))),
                        stringValue(rawContext.get("requestId")),
                        uuidValue(rawContext.get("patientId"), job.getPatientId()),
                        uuidValue(rawContext.get("documentId"), job.getDocumentId())
                );
            } catch (Exception ex) {
                log.warn("Unable to deserialize clinical document AI job context. jobId={}, tenantId={}, documentId={}, patientId={}, stage=REQUEST_CONTEXT_RESTORE, error={}",
                        job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), ex.getMessage());
                return fallback(job);
            }
        }

        Map<String, Object> asMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("tenantCode", tenantCode);
            payload.put("clinicId", clinicId);
            payload.put("actorAppUserId", actorAppUserId);
            payload.put("actorUsername", actorUsername);
            payload.put("tenantRole", tenantRole);
            payload.put("correlationId", correlationId);
            payload.put("requestId", requestId);
            payload.put("patientId", patientId);
            payload.put("documentId", documentId);
            return payload;
        }

        private static ProcessingRequestContext fallback(ClinicalAiJobEntity job) {
            String correlationId = UUID.randomUUID().toString();
            return new ProcessingRequestContext(
                    job.getTenantId(),
                    null,
                    null,
                    job.getRequestedByAppUserId(),
                    null,
                    null,
                    correlationId,
                    correlationId,
                    job.getPatientId(),
                    job.getDocumentId()
            );
        }

        private static UUID uuidValue(Object value, UUID fallback) {
            if (value == null) {
                return fallback;
            }
            try {
                return UUID.fromString(value.toString());
            } catch (IllegalArgumentException ex) {
                return fallback;
            }
        }

        private static String stringValue(Object value) {
            if (value == null) {
                return null;
            }
            String text = value.toString();
            return text.isBlank() ? null : text;
        }
    }
}
