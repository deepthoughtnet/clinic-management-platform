package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import com.deepthoughtnet.clinic.api.ai.dto.AiDraftResponse;
import com.deepthoughtnet.clinic.api.ai.service.AiDoctorCopilotService;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.dto.ClinicalMemoryRepairResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobType;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalDocumentTextExtractionResult;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalMemoryRepairCorrectedValue;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ClinicalDocumentAiExtractionService {
    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentAiExtractionService.class);
    private static final String ENTITY_TYPE = "CLINICAL_DOCUMENT_AI";
    private static final String FRIENDLY_AI_FAILURE_MESSAGE = "AI processing could not complete. Please retry.";
    private static final Pattern LAB_PATTERN = Pattern.compile(
            "(?i)\\b(hba1c|hb a1c|a1c|glycated hemoglobin|glycosylated hemoglobin|hemoglobin|hb|glucose|blood sugar|cholesterol|hdl|ldl|triglycerides|bilirubin|alt|ast|alp|alk phos|creatinine|egfr|estimated gfr|estimated glomerular filtration rate|crp|c-reactive protein)\\b[^\\d\\n]{0,20}([<>]?\\s*\\d+(?:\\.\\d+)?)"
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
    private final DeterministicLabFactParser deterministicLabFactParser;
    private final ObjectMapper objectMapper;
    private final long retryBackoffMs;
    private final int maxAttempts;

    @Autowired
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
        this(jobRepository,
                documentRepository,
                documentService,
                longitudinalMemoryService,
                appUserRepository,
                textExtractionService,
                aiDoctorCopilotService,
                storageService,
                auditEventPublisher,
                agentExecutionLogService,
                patientService,
                notificationSettingsService,
                new DeterministicLabFactParser(),
                objectMapper,
                retryBackoffMs,
                maxAttempts);
    }

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
                                               DeterministicLabFactParser deterministicLabFactParser,
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
        this.deterministicLabFactParser = deterministicLabFactParser;
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
    public ClinicalMemoryRepairResult repairClinicalMemory(UUID tenantId, UUID documentId, UUID actorAppUserId) {
        long startedAt = System.currentTimeMillis();
        log.info("[AI-MEMORY-REPAIR-START] tenantId={} documentId={} actorAppUserId={}", tenantId, documentId, actorAppUserId);
        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        ClinicalAiJobEntity latestJob = jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(
                        tenantId,
                        documentId,
                        ClinicalAiJobType.DOCUMENT_EXTRACTION)
                .orElse(null);
        String structuredJson = firstHasText(
                document.getAiExtractionStructuredJson(),
                latestJob == null ? null : latestJob.getResultJson(),
                document.getAiExtractionAcceptedJson()
        );
        log.info("[AI-MEMORY-REPAIR] tenantId={} patientId={} sourceDocumentId={} sourceStructuredJsonLength={} sourceAcceptedJsonLength={} sourceJobResultJsonLength={}",
                tenantId,
                document.getPatientId(),
                documentId,
                lengthOf(document.getAiExtractionStructuredJson()),
                lengthOf(document.getAiExtractionAcceptedJson()),
                latestJob == null ? 0 : lengthOf(latestJob.getResultJson()));
        if (!StringUtils.hasText(structuredJson)) {
            ClinicalMemoryRepairResult failed = new ClinicalMemoryRepairResult(
                    documentId,
                    "FAILED",
                    OffsetDateTime.now(),
                    actorAppUserId,
                    0,
                    0,
                    0,
                    List.of(),
                    0,
                    "Stored AI extraction data not found for document"
            );
            document.markClinicalMemoryRepair(actorAppUserId, failed.status(), failed.message(), 0, 0, 0, 0, "[]");
            documentRepository.save(document);
            log.info("[AI-MEMORY-REPAIR] tenantId={} patientId={} documentId={} status={} deletedPendingConceptCount={} insertedConceptCount={} filteredPollutedConceptCount={} correctedValues={} durationMs={}",
                    tenantId,
                    document.getPatientId(),
                    documentId,
                    failed.status(),
                    failed.deletedPendingConceptCount(),
                    failed.insertedConceptCount(),
                    failed.filteredPollutedConceptCount(),
                    toJson(failed.correctedValues()),
                    System.currentTimeMillis() - startedAt);
            log.info("[AI-MEMORY-REPAIR-END] tenantId={} documentId={} status={} durationMs={}", tenantId, documentId, failed.status(), System.currentTimeMillis() - startedAt);
            return failed;
        }
        String sourceSummary = firstHasText(document.getAiExtractionSummary(), latestJob == null ? null : latestJob.getSummaryText(), "Clinical memory repair");
        String legacySourceText = buildLegacyRepairSourceText(structuredJson, latestJob);
        structuredJson = normalizeStoredStructuredJson(document, structuredJson, legacySourceText, sourceSummary);
        String sourceText = buildRepairSourceText(structuredJson, latestJob);
        try {
            int beforeConceptCount = historyCount(tenantId, document.getPatientId());
            String hba1cBefore = latestHbA1cValue(tenantId, document.getPatientId());
            ClinicalMemoryRepairResult result = longitudinalMemoryService.repairPendingConcepts(document, structuredJson, sourceText, document.getAiExtractionConfidence(), sourceSummary, actorAppUserId);
            document.markClinicalMemoryRepair(
                    actorAppUserId,
                    result.status(),
                    result.message(),
                    result.deletedPendingConceptCount(),
                    result.insertedConceptCount(),
                    result.skippedAcceptedConceptCount(),
                    result.filteredPollutedConceptCount(),
                    toJson(result.correctedValues())
            );
            documentRepository.save(document);
            auditEventPublisher.record(new AuditEventCommand(
                    tenantId,
                    AuditEntityType.DOCUMENT,
                    documentId,
                    "clinical.document.clinical_memory.repaired",
                    actorAppUserId,
                    OffsetDateTime.now(),
                    "Repaired longitudinal clinical memory from stored AI extraction data",
                    "{\"documentId\":\"%s\"}".formatted(documentId)
            ));
            log.info("[AI-MEMORY-REPAIR] tenantId={} patientId={} documentId={} status={} deletedPendingConceptCount={} insertedConceptCount={} filteredPollutedConceptCount={} correctedValues={} durationMs={}",
                    tenantId,
                    document.getPatientId(),
                    documentId,
                    result.status(),
                    result.deletedPendingConceptCount(),
                    result.insertedConceptCount(),
                    result.filteredPollutedConceptCount(),
                    toJson(result.correctedValues()),
                    System.currentTimeMillis() - startedAt);
            int afterConceptCount = historyCount(tenantId, document.getPatientId());
            String hba1cAfter = latestHbA1cValue(tenantId, document.getPatientId());
            log.info("[AI-MEMORY-REPAIR] tenantId={} patientId={} sourceDocumentId={} beforeConceptCount={} deletedPendingConceptCount={} insertedConceptCount={} filteredPollutedConceptCount={} hba1cBefore={} hba1cAfter={} status={} error={}",
                    tenantId,
                    document.getPatientId(),
                    documentId,
                    beforeConceptCount,
                    result.deletedPendingConceptCount(),
                    result.insertedConceptCount(),
                    result.filteredPollutedConceptCount(),
                    hba1cBefore,
                    hba1cAfter,
                    result.status(),
                    null);
            log.info("[AI-MEMORY-REPAIR] tenantId={} patientId={} sourceDocumentId={} afterConceptCount={} hba1cAfter={} status={}",
                    tenantId,
                    document.getPatientId(),
                    documentId,
                    afterConceptCount,
                    hba1cAfter,
                    result.status());
            log.info("[AI-MEMORY-REPAIR-END] tenantId={} documentId={} status={} durationMs={}", tenantId, documentId, result.status(), System.currentTimeMillis() - startedAt);
            return result;
        } catch (RuntimeException ex) {
            String technicalMessage = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
            ClinicalMemoryRepairResult failed = new ClinicalMemoryRepairResult(
                    documentId,
                    "FAILED",
                    OffsetDateTime.now(),
                    actorAppUserId,
                    0,
                    0,
                    0,
                    List.of(),
                    0,
                    "Clinical memory repair failed safely"
            );
            document.markClinicalMemoryRepair(actorAppUserId, failed.status(), failed.message(), 0, 0, 0, 0, "[]");
            documentRepository.save(document);
            log.warn("[AI-MEMORY-REPAIR] tenantId={} patientId={} documentId={} status={} deletedPendingConceptCount={} insertedConceptCount={} filteredPollutedConceptCount={} correctedValues={} durationMs={} error={}",
                    tenantId,
                    document.getPatientId(),
                    documentId,
                    failed.status(),
                    failed.deletedPendingConceptCount(),
                    failed.insertedConceptCount(),
                    failed.filteredPollutedConceptCount(),
                    toJson(failed.correctedValues()),
                    System.currentTimeMillis() - startedAt,
                    technicalMessage, ex);
            log.info("[AI-MEMORY-REPAIR-END] tenantId={} documentId={} status={} durationMs={}", tenantId, documentId, failed.status(), System.currentTimeMillis() - startedAt);
            return failed;
        }
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
            traceOcrExtraction(document, textResult);
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
            traceAiResponse(document, response);

            Map<String, Object> structuredData = new LinkedHashMap<>();
            if (response.structuredData() != null) {
                structuredData.putAll(response.structuredData());
            }
            structuredData.putIfAbsent("documentType", document.getDocumentType().name());
            structuredData.putIfAbsent("possibleAbnormalFindings", abnormalFindings(textResult.text()));
            structuredData.putIfAbsent("ocrProvider", textResult.provider());
            structuredData.putIfAbsent("ocrStatus", textResult.status());
            structuredData = normalizeExtractionSchema(document, textResult, response, structuredData);
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
            if (context.reprocessRequested()) {
                document.markAiRetryCompleted(reviewStatus, summary);
                documentRepository.save(document);
            }
            log.info("Clinical document AI extraction completed. jobId={}, tenantId={}, documentId={}, patientId={}, correlationId={}, stage=AI_EXTRACTION_COMPLETED, reviewStatus={}",
                    job.getId(), job.getTenantId(), job.getDocumentId(), job.getPatientId(), context.correlationId(), reviewStatus);
        } catch (RuntimeException ex) {
            boolean retryable = job.getAttemptCount() + 1 < maxAttempts;
            String technicalMessage = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
            document.markAiExtractionFailed(job.getProvider(), job.getModel(), FRIENDLY_AI_FAILURE_MESSAGE);
            documentRepository.save(document);
            job.markFailed(technicalMessage, retryable, retryBackoffMs);
            jobRepository.save(job);
            if (context.reprocessRequested()) {
                document.markAiRetryCompleted("FAILED", FRIENDLY_AI_FAILURE_MESSAGE);
                documentRepository.save(document);
            }
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
        Object factualFindings = structuredData == null ? null : structuredData.get("factualFindings");
        int labCount = countTraceItems(factualFindings instanceof Map<?, ?> map ? map.get("labResults") : null);
        int conditionCount = countTraceItems(factualFindings instanceof Map<?, ?> map ? map.get("conditions") : null);
        int riskFlagCount = countTraceItems(factualFindings instanceof Map<?, ?> map ? map.get("riskFlags") : null);
        int recommendationCount = countTraceItems(structuredData == null ? null : structuredData.get("recommendations"));
        log.info("[AI-DOC-EXTRACTION-SCHEMA] documentId={} hasFactualFindings={} labResultCount={} conditionCount={} riskFlagCount={} recommendationCount={} summaryPresent={}",
                document.getId(),
                factualFindings instanceof Map<?, ?>,
                labCount,
                conditionCount,
                riskFlagCount,
                recommendationCount,
                structuredData != null && hasText(stringValue(structuredData.get("summary"))));
        log.info("[AI-DOC-PIPELINE-TRACE] documentId={} stage=AI_NORMALIZED sourceUsed={} keysPresent={} labResultCount={} conditionCount={} riskFlagCount={}",
                document.getId(),
                factualFindings instanceof Map<?, ?> ? "STRUCTURED_LABS" : "LEGACY_FIELDS",
                structuredData == null ? List.of() : new ArrayList<>(structuredData.keySet()),
                labCount,
                conditionCount,
                riskFlagCount);
        log.info(
                "[JEEV-LONG-MEM-TRACE] parsed-extraction tenantId={} patientId={} consultationId={} documentId={} extractedJsonKeys={} extractedLabCount={} extractedConditionCount={} extractedRiskFlagCount={}",
                job.getTenantId(),
                job.getPatientId(),
                document.getConsultationId(),
                document.getId(),
                structuredData == null ? List.of() : new ArrayList<>(structuredData.keySet()),
                labCount,
                conditionCount,
                riskFlagCount
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

    private String buildRepairSourceText(String structuredJson, ClinicalAiJobEntity latestJob) {
        Map<String, Object> extracted = parseStructuredJson(structuredJson);
        List<String> evidence = new ArrayList<>();
        collectRepairEvidenceText(extracted.get("factualFindings"), evidence, null);
        if (latestJob != null && StringUtils.hasText(latestJob.getResultJson())) {
            Map<String, Object> latestExtracted = parseStructuredJson(latestJob.getResultJson());
            collectRepairEvidenceText(latestExtracted.get("factualFindings"), evidence, null);
            collectRepairEvidenceText(latestExtracted, evidence, null);
        }
        return String.join("\n", new java.util.LinkedHashSet<>(evidence));
    }

    private String buildLegacyRepairSourceText(String structuredJson, ClinicalAiJobEntity latestJob) {
        Map<String, Object> extracted = parseStructuredJson(structuredJson);
        List<String> evidence = new ArrayList<>();
        collectRepairEvidenceText(extracted, evidence, null);
        if (latestJob != null && StringUtils.hasText(latestJob.getResultJson())) {
            collectRepairEvidenceText(parseStructuredJson(latestJob.getResultJson()), evidence, null);
        }
        return String.join("\n", new java.util.LinkedHashSet<>(evidence));
    }

    private void collectRepairEvidenceText(Object value, List<String> target, String key) {
        if (value == null || target == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childKey = entry.getKey() == null ? null : entry.getKey().toString();
                collectRepairEvidenceText(entry.getValue(), target, childKey);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                collectRepairEvidenceText(item, target, key);
            }
            return;
        }
        String text = normalizeRepairEvidenceText(value.toString());
        if (text.isBlank()) {
            return;
        }
        if (isEvidenceKey(key)) {
            target.add(text);
            return;
        }
        if (text.length() > 8 && text.length() < 400 && !looksLikeNarrativeRecommendation(text)) {
            target.add(text);
        }
    }

    private String normalizeRepairEvidenceText(String text) {
        if (!hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        String prefix = "Possible abnormal finding detected:";
        if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return normalized.substring(prefix.length()).trim();
        }
        return normalized;
    }

    private boolean looksLikeNarrativeRecommendation(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("review ")
                || normalized.contains("discuss ")
                || normalized.contains("recommend ")
                || normalized.contains("consider ")
                || normalized.contains("monitor ")
                || normalized.contains("follow up")
                || normalized.contains("follow-up")
                || normalized.contains("lifestyle modification")
                || normalized.contains("patient instruction")
                || normalized.contains("doctor advice")
                || normalized.contains("suggested action")
                || normalized.contains("clinical summary")
                || normalized.contains("ai summary");
    }

    private boolean isEvidenceKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("evidence")
                || normalized.contains("sourcetext")
                || normalized.contains("ocrtext")
                || normalized.contains("rawtext")
                || normalized.contains("finding")
                || normalized.contains("abnormalfinding");
    }

    private Map<String, Object> normalizeExtractionSchema(ClinicalDocumentEntity document,
                                                          ClinicalDocumentTextExtractionResult textResult,
                                                          AiDraftResponse response,
                                                          Map<String, Object> rawStructuredData) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("documentType", firstHasText(stringValue(rawStructuredData.get("documentType")), document.getDocumentType() == null ? null : document.getDocumentType().name()));
        normalized.put("reportDate", document.getReportDate() == null ? null : document.getReportDate().toString());
        normalized.put("factualFindings", buildFactualFindings(document, textResult, rawStructuredData));
        normalized.put("summary", firstHasText(stringValue(rawStructuredData.get("summary")), stringValue(rawStructuredData.get("answer")), response.draft()));
        normalized.put("recommendations", collectRecommendationList(rawStructuredData, response));
        normalized.put("limitations", collectLimitations(rawStructuredData, response));
        normalized.put("confidence", normalizeConfidence(rawStructuredData.get("confidence"), response.confidence()));
        normalized.put("ocrProvider", textResult.provider());
        normalized.put("ocrStatus", textResult.status());
        normalized.put("possibleAbnormalFindings", abnormalFindings(textResult.text()));
        return normalized;
    }

    private String normalizeStoredStructuredJson(ClinicalDocumentEntity document,
                                                 String structuredJson,
                                                 String sourceText,
                                                 String sourceSummary) {
        Map<String, Object> rawStructuredData = parseStructuredJson(structuredJson);
        Map<String, Object> normalized = normalizeExtractionSchema(
                document,
                new ClinicalDocumentTextExtractionResult(
                        "UNKNOWN",
                        firstHasText(document.getOcrStatus(), "COMPLETED"),
                        sourceText
                ),
                new AiDraftResponse(
                        true,
                        false,
                        "Normalized stored extraction",
                        null,
                        null,
                        sourceSummary,
                        rawStructuredData,
                        document.getAiExtractionConfidence(),
                        List.of(),
                        List.of(),
                        null
                ),
                rawStructuredData
        );
        return toJson(normalized);
    }

    private Map<String, Object> buildFactualFindings(ClinicalDocumentEntity document,
                                                     ClinicalDocumentTextExtractionResult textResult,
                                                     Map<String, Object> rawStructuredData) {
        Map<String, Object> factualFindings = new LinkedHashMap<>();
        List<Map<String, Object>> labResults = normalizeLabFacts(document, textResult.text(), rawStructuredData);
        List<Map<String, Object>> conditions = normalizeConditions(rawStructuredData, textResult.text());
        List<Map<String, Object>> riskFlags = normalizeRiskFlags(rawStructuredData, textResult.text(), labResults, conditions);
        factualFindings.put("labResults", labResults);
        factualFindings.put("conditions", conditions);
        factualFindings.put("riskFlags", riskFlags);
        return factualFindings;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeLabFacts(ClinicalDocumentEntity document, String ocrText, Map<String, Object> rawStructuredData) {
        LinkedHashMap<String, Map<String, Object>> normalizedByKey = new LinkedHashMap<>();

        addNormalizedLabFacts(
                normalizedByKey,
                document.getId(),
                ocrText,
                deterministicLabFactParser.parse(document.getId(), ocrText, extractDetectedLabLines(ocrText)),
                "OCR_TABLE_LINES",
                true
        );

        Object factualFindings = rawStructuredData.get("factualFindings");
        Object candidate = factualFindings instanceof Map<?, ?> factualMap ? factualMap.get("labResults") : null;
        if (candidate != null) {
            addNormalizedLabFacts(normalizedByKey, document.getId(), ocrText, candidate, "STRUCTURED_LABS", false);
        }

        List<Map<String, Object>> classificationDetails = extractClassificationDetailLabResults(rawStructuredData);
        if (!classificationDetails.isEmpty()) {
            addNormalizedLabFacts(normalizedByKey, document.getId(), ocrText, classificationDetails, "AI_CLASSIFICATION_DETAILS", false);
        }

        Object legacyCandidate = firstNonNull(rawStructuredData.get("labResults"), rawStructuredData.get("labValues"), rawStructuredData.get("labs"), rawStructuredData.get("results"));
        if (legacyCandidate != null) {
            addNormalizedLabFacts(normalizedByKey, document.getId(), ocrText, legacyCandidate, "LEGACY_FIELDS", false);
        }

        return new ArrayList<>(normalizedByKey.values());
    }

    @SuppressWarnings("unchecked")
    private void addNormalizedLabFacts(LinkedHashMap<String, Map<String, Object>> target,
                                       UUID documentId,
                                       String ocrText,
                                       Object candidate,
                                       String sourceUsed,
                                       boolean preferExisting) {
        if (candidate == null) {
            return;
        }
        if (candidate instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> fact)) {
                    continue;
                }
                Map<String, Object> row = normalizeLabFact(documentId, ocrText, (Map<String, Object>) fact, null, null, sourceUsed, "labResults[]");
                addFactRow(target, row, preferExisting);
            }
            return;
        }
        if (candidate instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> row = normalizeLabFact(documentId, ocrText, null, stringValue(entry.getKey()), entry.getValue(), sourceUsed, "labs." + stringValue(entry.getKey()));
                addFactRow(target, row, preferExisting);
            }
        }
    }

    private void addFactRow(LinkedHashMap<String, Map<String, Object>> target, Map<String, Object> row, boolean preferExisting) {
        if (row == null) {
            return;
        }
        String key = stringValue(row.get("canonicalKey"));
        if (!hasText(key)) {
            return;
        }
        if (preferExisting) {
            target.putIfAbsent(key, row);
            return;
        }
        target.putIfAbsent(key, row);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractClassificationDetailLabResults(Map<String, Object> rawStructuredData) {
        if (rawStructuredData == null) {
            return List.of();
        }
        Object answer = rawStructuredData.get("answer");
        if (!(answer instanceof Map<?, ?> answerMap)) {
            return List.of();
        }
        Object classification = answerMap.get("classification");
        if (!(classification instanceof Map<?, ?> classificationMap)) {
            return List.of();
        }
        List<Map<String, Object>> derived = new ArrayList<>();
        for (Map.Entry<?, ?> classificationEntry : classificationMap.entrySet()) {
            String classificationKey = stringValue(classificationEntry.getKey());
            Object classificationValue = classificationEntry.getValue();
            if (!(classificationValue instanceof Map<?, ?> sectionMap)) {
                continue;
            }
            Object details = sectionMap.get("details");
            if (!(details instanceof Iterable<?> iterable)) {
                continue;
            }
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> detail)) {
                    continue;
                }
                String testName = firstHasText(stringValue(detail.get("test")), stringValue(detail.get("testName")), stringValue(detail.get("label")));
                String canonicalKey = canonicalLabKey(testName);
                if (!hasText(canonicalKey)) {
                    continue;
                }
                String result = firstHasText(stringValue(detail.get("result")), stringValue(detail.get("value")));
                String referenceRange = stringValue(detail.get("referenceRange"));
                String flag = normalizeFlagLabel(firstHasText(stringValue(detail.get("flag")), stringValue(detail.get("status")), stringValue(detail.get("abnormality"))));
                String evidenceText = buildDetailEvidence(testName, result, referenceRange, flag);
                Map<String, Object> fact = new LinkedHashMap<>();
                fact.put("testName", displayTestName(canonicalKey, testName));
                fact.put("canonicalKey", canonicalKey);
                fact.put("value", result);
                fact.put("unit", extractUnitFromResult(result));
                fact.put("referenceRange", referenceRange);
                fact.put("flag", flag);
                fact.put("evidenceText", evidenceText);
                fact.put("sourcePath", "answer.classification." + firstHasText(classificationKey, "unknown") + ".details[]");
                derived.add(fact);
            }
        }
        return derived;
    }

    private List<Map<String, Object>> extractOcrFallbackLabResults(UUID documentId, String ocrText) {
        if (!hasText(ocrText)) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        addOcrFallbackLabResult(results, documentId, ocrText, "HbA1c", "hba1c");
        addOcrFallbackLabResult(results, documentId, ocrText, "Estimated Average Glucose", "estimated_average_glucose");
        addOcrFallbackLabResult(results, documentId, ocrText, "Random Blood Sugar", "blood_sugar");
        addOcrFallbackLabResult(results, documentId, ocrText, "Total Cholesterol", "cholesterol");
        addOcrFallbackLabResult(results, documentId, ocrText, "LDL Cholesterol", "ldl");
        addOcrFallbackLabResult(results, documentId, ocrText, "HDL Cholesterol", "hdl");
        addOcrFallbackLabResult(results, documentId, ocrText, "Triglycerides", "triglycerides");
        addOcrFallbackLabResult(results, documentId, ocrText, "Hemoglobin", "hemoglobin");
        addOcrFallbackLabResult(results, documentId, ocrText, "Creatinine", "creatinine");
        addOcrFallbackLabResult(results, documentId, ocrText, "eGFR", "egfr");
        addOcrFallbackLabResult(results, documentId, ocrText, "CRP", "crp");
        addOcrFallbackLabResult(results, documentId, ocrText, "ALT", "alt");
        addOcrFallbackLabResult(results, documentId, ocrText, "AST", "ast");
        return results;
    }

    private void addOcrFallbackLabResult(List<Map<String, Object>> results,
                                         UUID documentId,
                                         String ocrText,
                                         String testName,
                                         String canonicalKey) {
        String line = findEvidenceLineForLab(ocrText, canonicalKey);
        if (!hasText(line)) {
            return;
        }
        String value = normalizeLabValue(canonicalKey, null, line);
        String unit = normalizeLabUnit(canonicalKey, null, null, line);
        String flag = normalizeFlag(null, line, canonicalKey, value);
        String referenceRange = extractReferenceRangeFromEvidence(line, value, unit, flag);
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("testName", testName);
        fact.put("canonicalKey", canonicalKey);
        fact.put("value", value);
        fact.put("unit", unit);
        fact.put("referenceRange", referenceRange);
        fact.put("flag", flag);
        fact.put("evidenceText", line.trim());
        fact.put("sourcePath", "ocr.labLines");
        log.info("[AI-DOC-PIPELINE-TRACE] documentId={} stage=OCR_FALLBACK conceptKey={} sourcePath={} rawValue={} evidenceText={}",
                documentId, canonicalKey, "ocr.labLines", value, summarizeText(line));
        results.add(fact);
    }

    private Map<String, Object> normalizeLabFact(UUID documentId,
                                                 String ocrText,
                                                 Map<String, Object> fact,
                                                 String fallbackKey,
                                                 Object fallbackValue,
                                                 String sourceUsed,
                                                 String sourceField) {
        String sourceKey = firstHasText(
                stringValue(fact == null ? null : firstNonNull(fact.get("canonicalKey"), fact.get("conceptKey"), fact.get("key"))),
                fallbackKey,
                stringValue(fact == null ? null : firstNonNull(fact.get("testName"), fact.get("label")))
        );
        String canonicalKey = canonicalLabKey(sourceKey);
        String testName = displayTestName(canonicalKey, stringValue(fact == null ? null : firstNonNull(fact.get("testName"), fact.get("label"))));
        String rawValue = firstHasText(
                stringValue(fact == null ? null : firstNonNull(fact.get("value"), fact.get("result"), fact.get("valueText"))),
                stringValue(fallbackValue)
        );
        if (isBlockedNarrativeLabSource(rawValue)) {
            log.info("[AI-DOC-PIPELINE-TRACE] documentId={} sourceUsed={} conceptKey={} proposedValue={} unit={} sourceField={} evidenceText={} accepted={} rejectionReason={}",
                    documentId, sourceUsed, canonicalKey, summarizeValue(rawValue), null, sourceField, summarizeText(rawValue), false, "BLOCKED_NARRATIVE_SOURCE");
            return null;
        }
        String evidenceText = firstHasText(
                stringValue(fact == null ? null : firstNonNull(fact.get("evidenceText"), fact.get("evidence"), fact.get("sourceText"))),
                rawValueLooksLikeEvidence(canonicalKey, rawValue) ? rawValue : null,
                findEvidenceLineForLab(ocrText, canonicalKey)
        );
        if (isBlockedNarrativeLabSource(evidenceText)) {
            log.info("[AI-DOC-PIPELINE-TRACE] documentId={} sourceUsed={} conceptKey={} proposedValue={} unit={} sourceField={} evidenceText={} accepted={} rejectionReason={}",
                    documentId, sourceUsed, canonicalKey, summarizeValue(rawValue), null, sourceField, summarizeText(evidenceText), false, "BLOCKED_NARRATIVE_EVIDENCE");
            return null;
        }
        String normalizedValue = normalizeLabValue(canonicalKey, rawValue, evidenceText);
        String unit = normalizeLabUnit(canonicalKey, stringValue(fact == null ? null : firstNonNull(fact.get("unit"), fact.get("valueUnit"))), rawValue, evidenceText);
        String referenceRange = stringValue(fact == null ? null : fact.get("referenceRange"));
        String flag = normalizeFlag(stringValue(fact == null ? null : fact.get("flag")), evidenceText, canonicalKey, normalizedValue);
        if (fact != null && hasText(stringValue(fact.get("sourcePath")))) {
            sourceField = stringValue(fact.get("sourcePath"));
        }

        String rejectionReason = validateLabFact(canonicalKey, testName, normalizedValue, evidenceText);
        boolean accepted = rejectionReason == null;
        log.info("[AI-DOC-LAB-FACT] documentId={} canonicalKey={} testName={} value={} unit={} flag={} accepted={} reason={}",
                documentId, canonicalKey, testName, normalizedValue, unit, flag, accepted, accepted ? "ACCEPTED" : rejectionReason);
        log.info("[AI-DOC-PIPELINE-TRACE] documentId={} sourceUsed={} conceptKey={} proposedValue={} unit={} sourceField={} evidenceText={} accepted={} rejectionReason={}",
                documentId, sourceUsed, canonicalKey, normalizedValue, unit, sourceField, summarizeText(evidenceText), accepted, rejectionReason);
        if (!accepted) {
            return null;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("testName", testName);
        row.put("canonicalKey", canonicalKey);
        row.put("value", normalizedValue);
        row.put("unit", unit);
        row.put("referenceRange", referenceRange);
        row.put("flag", flag);
        row.put("evidenceText", evidenceText);
        return row;
    }

    private List<Map<String, Object>> normalizeConditions(Map<String, Object> rawStructuredData, String ocrText) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        Object factualFindings = rawStructuredData.get("factualFindings");
        Object candidate = factualFindings instanceof Map<?, ?> factualMap ? factualMap.get("conditions") : null;
        if (candidate == null) {
            candidate = firstNonNull(rawStructuredData.get("knownConditions"), rawStructuredData.get("chronicConditions"), rawStructuredData.get("conditions"), rawStructuredData.get("diagnosesMentioned"));
        }
        if (candidate instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Map<String, Object> condition = normalizeConditionFact(item, ocrText);
                if (condition != null) {
                    normalized.add(condition);
                }
            }
        } else {
            Map<String, Object> condition = normalizeConditionFact(candidate, ocrText);
            if (condition != null) {
                normalized.add(condition);
            }
        }
        if (normalized.isEmpty() && hasText(ocrText) && ocrText.toLowerCase(Locale.ROOT).contains("diabetic")) {
            normalized.add(Map.of(
                    "canonicalKey", "diabetes_mellitus",
                    "label", "Diabetes Mellitus",
                    "evidenceText", firstHasText(findLine(ocrText, "diabetes"), "Known diabetic")
            ));
        }
        return dedupeFactRows(normalized, "canonicalKey");
    }

    private Map<String, Object> normalizeConditionFact(Object item, String ocrText) {
        String label;
        String canonicalKey;
        String evidenceText;
        if (item instanceof Map<?, ?> fact) {
            label = firstHasText(stringValue(fact.get("label")), stringValue(fact.get("name")), stringValue(fact.get("value")));
            canonicalKey = firstHasText(stringValue(fact.get("canonicalKey")), stringValue(fact.get("conceptKey")), stringValue(fact.get("key")));
            evidenceText = firstHasText(stringValue(fact.get("evidenceText")), stringValue(fact.get("evidence")), findLine(ocrText, label));
        } else {
            label = stringValue(item);
            canonicalKey = null;
            evidenceText = firstHasText(findLine(ocrText, label), label);
        }
        if (!hasText(label)) {
            return null;
        }
        String normalizedLabel = label.toLowerCase(Locale.ROOT).contains("diabet") ? "Diabetes Mellitus" : label.trim();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("canonicalKey", firstHasText(canonicalKey, normalizedLabel.toLowerCase(Locale.ROOT).contains("diabet") ? "diabetes_mellitus" : slug(normalizedLabel)));
        row.put("label", normalizedLabel);
        row.put("evidenceText", evidenceText);
        return row;
    }

    private List<Map<String, Object>> normalizeRiskFlags(Map<String, Object> rawStructuredData,
                                                         String ocrText,
                                                         List<Map<String, Object>> labResults,
                                                         List<Map<String, Object>> conditions) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        Object factualFindings = rawStructuredData.get("factualFindings");
        Object candidate = factualFindings instanceof Map<?, ?> factualMap ? factualMap.get("riskFlags") : null;
        if (candidate == null) {
            candidate = rawStructuredData.get("riskFlags");
        }
        if (candidate instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Map<String, Object> risk = normalizeRiskFact(item, ocrText);
                if (risk != null) {
                    normalized.add(risk);
                }
            }
        }
        boolean diabetesCondition = conditions.stream().anyMatch(condition -> "diabetes_mellitus".equals(stringValue(condition.get("canonicalKey"))));
        if (diabetesCondition || labResults.stream().anyMatch(lab -> "hba1c".equals(stringValue(lab.get("canonicalKey"))) && "HIGH".equalsIgnoreCase(stringValue(lab.get("flag"))))) {
            normalized.add(Map.of("canonicalKey", "diabetes_risk", "label", "Diabetes", "evidenceText", firstHasText(findLine(ocrText, "hba1c"), "HbA1c evidence")));
        }
        if (labResults.stream().anyMatch(lab -> Set.of("cholesterol", "ldl", "hdl", "triglycerides").contains(stringValue(lab.get("canonicalKey"))) && Set.of("HIGH", "LOW").contains(stringValue(lab.get("flag")).toUpperCase(Locale.ROOT)))) {
            normalized.add(Map.of("canonicalKey", "lipid_risk", "label", "Dyslipidemia", "evidenceText", firstHasText(findLine(ocrText, "cholesterol"), "Lipid evidence")));
        }
        return dedupeFactRows(normalized, "canonicalKey");
    }

    private Map<String, Object> normalizeRiskFact(Object item, String ocrText) {
        if (!(item instanceof Map<?, ?> fact)) {
            return null;
        }
        String label = firstHasText(stringValue(fact.get("label")), stringValue(fact.get("name")), stringValue(fact.get("value")));
        if (!hasText(label)) {
            return null;
        }
        String canonicalKey = firstHasText(stringValue(fact.get("canonicalKey")), stringValue(fact.get("conceptKey")), stringValue(fact.get("key")));
        if (!hasText(canonicalKey)) {
            canonicalKey = label.toLowerCase(Locale.ROOT).contains("diabet") ? "diabetes_risk" : "lipid_risk";
        }
        return Map.of(
                "canonicalKey", canonicalKey,
                "label", label.toLowerCase(Locale.ROOT).contains("diabet") ? "Diabetes" : label,
                "evidenceText", firstHasText(stringValue(fact.get("evidenceText")), stringValue(fact.get("evidence")), findLine(ocrText, label))
        );
    }

    private List<Map<String, Object>> dedupeFactRows(List<Map<String, Object>> rows, String keyField) {
        LinkedHashMap<String, Map<String, Object>> deduped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = stringValue(row.get(keyField));
            if (hasText(key)) {
                deduped.putIfAbsent(key, row);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private List<String> collectRecommendationList(Map<String, Object> rawStructuredData, AiDraftResponse response) {
        LinkedHashSet<String> recommendations = new LinkedHashSet<>();
        addStrings(recommendations, rawStructuredData.get("recommendations"));
        addStrings(recommendations, rawStructuredData.get("suggestedActions"));
        addStrings(recommendations, rawStructuredData.get("followUpSuggestions"));
        if (response.suggestedActions() != null) {
            recommendations.addAll(response.suggestedActions());
        }
        return new ArrayList<>(recommendations);
    }

    private List<String> collectLimitations(Map<String, Object> rawStructuredData, AiDraftResponse response) {
        LinkedHashSet<String> limitations = new LinkedHashSet<>();
        addStrings(limitations, rawStructuredData.get("limitations"));
        addStrings(limitations, rawStructuredData.get("warnings"));
        if (response.warnings() != null) {
            limitations.addAll(response.warnings());
        }
        return new ArrayList<>(limitations);
    }

    private void addStrings(Collection<String> target, Object value) {
        if (target == null || value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (hasText(stringValue(item))) {
                    target.add(stringValue(item).trim());
                }
            }
            return;
        }
        if (hasText(stringValue(value))) {
            target.add(stringValue(value).trim());
        }
    }

    private String validateLabFact(String canonicalKey, String testName, String value, String evidenceText) {
        if (!hasText(canonicalKey) || !hasText(testName) || !hasText(value) || !hasText(evidenceText)) {
            return "MISSING_REQUIRED_FIELDS";
        }
        String normalizedEvidence = evidenceText.trim().toLowerCase(Locale.ROOT);
        if (startsWithAny(normalizedEvidence, "review", "discuss", "recommend", "consider", "possible abnormal finding detected")) {
            return "RECOMMENDATION_TEXT";
        }
        if (!normalizedEvidence.contains(value.toLowerCase(Locale.ROOT))) {
            return "VALUE_NOT_IN_EVIDENCE";
        }
        if ("hba1c".equals(canonicalKey)) {
            if (!containsAny(normalizedEvidence, "hba1c", "hb a1c", "a1c", "glycated hemoglobin", "glycosylated hemoglobin")) {
                return "HBA1C_LABEL_MISSING";
            }
            if (normalizedEvidence.contains("hemoglobin") && !normalizedEvidence.contains("hba1c") && !normalizedEvidence.contains("a1c")) {
                return "HEMOGLOBIN_MISMATCH";
            }
        }
        if ("blood_sugar".equals(canonicalKey) && !containsAny(normalizedEvidence, "blood sugar", "glucose", "rbs")) {
            return "BLOOD_SUGAR_LABEL_MISSING";
        }
        return null;
    }

    private String findEvidenceLineForLab(String ocrText, String canonicalKey) {
        if (!hasText(canonicalKey)) {
            return null;
        }
        return switch (canonicalKey) {
            case "hba1c" -> findLine(ocrText, "HbA1c", "Hb A1c", "A1c", "Glycated Hemoglobin", "Glycosylated Hemoglobin");
            case "blood_sugar" -> findLine(ocrText, "Random Blood Sugar", "Blood Sugar", "Glucose", "RBS");
            case "estimated_average_glucose" -> findLine(ocrText, "Estimated Average Glucose", "Average Glucose", "Glucose");
            case "cholesterol" -> findLine(ocrText, "Total Cholesterol", "Cholesterol");
            case "ldl" -> findLine(ocrText, "LDL Cholesterol", "LDL");
            case "hdl" -> findLine(ocrText, "HDL Cholesterol", "HDL");
            case "triglycerides" -> findLine(ocrText, "Triglycerides");
            case "hemoglobin" -> findLine(ocrText, "Hemoglobin");
            case "creatinine" -> findLine(ocrText, "Creatinine", "Serum Creatinine");
            case "egfr" -> findLine(ocrText, "eGFR", "Estimated GFR", "Estimated Glomerular Filtration Rate");
            case "crp" -> findLine(ocrText, "CRP", "C-Reactive Protein", "C Reactive Protein");
            case "alt" -> findLine(ocrText, "ALT", "SGPT", "Alanine Aminotransferase");
            case "ast" -> findLine(ocrText, "AST", "SGOT", "Aspartate Aminotransferase");
            default -> null;
        };
    }

    private String normalizeLabValue(String canonicalKey, String rawValue, String evidenceText) {
        if (!hasText(canonicalKey)) {
            return null;
        }
        if ("hba1c".equals(canonicalKey)) {
            return firstHasText(decimalFrom(rawValue), decimalFrom(extractLabelBoundValue(evidenceText, "hba1c", "hb a1c", "a1c", "glycated hemoglobin", "glycosylated hemoglobin")));
        }
        return firstHasText(numberFrom(rawValue), numberFrom(extractLabelBoundValue(evidenceText, evidenceLabelsFor(canonicalKey))));
    }

    private String normalizeLabUnit(String canonicalKey, String unit, String rawValue, String evidenceText) {
        if (hasText(unit)) {
            return canonicalUnit(unit);
        }
        if (hasText(rawValue) && rawValue.toLowerCase(Locale.ROOT).contains("mg/dl")) {
            return "mg/dL";
        }
        if (hasText(rawValue) && rawValue.contains("%")) {
            return "%";
        }
        return switch (canonicalKey) {
            case "hba1c" -> hasText(evidenceText) && evidenceText.contains("%") ? "%" : "%";
            case "egfr" -> "mL/min/1.73m2";
            case "crp" -> hasText(evidenceText) && evidenceText.toLowerCase(Locale.ROOT).contains("mg/l") ? "mg/L" : "mg/L";
            case "alt", "ast" -> hasText(evidenceText) && evidenceText.toLowerCase(Locale.ROOT).contains("u/l") ? "U/L" : "U/L";
            default -> hasText(evidenceText) && evidenceText.toLowerCase(Locale.ROOT).contains("mg/dl") ? "mg/dL" : "mg/dL";
        };
    }

    private String normalizeFlag(String rawFlag, String evidenceText, String canonicalKey, String value) {
        if (hasText(rawFlag)) {
            return rawFlag.trim().toUpperCase(Locale.ROOT);
        }
        if (hasText(evidenceText)) {
            String normalized = evidenceText.toLowerCase(Locale.ROOT);
            if (normalized.contains(" high")) return "HIGH";
            if (normalized.contains(" low")) return "LOW";
        }
        BigDecimal numeric = parseNumber(value);
        if (numeric == null) {
            return "UNKNOWN";
        }
        return switch (canonicalKey) {
            case "hba1c" -> numeric.compareTo(new BigDecimal("6.5")) >= 0 ? "HIGH" : "UNKNOWN";
            case "estimated_average_glucose" -> numeric.compareTo(new BigDecimal("154")) >= 0 ? "HIGH" : "UNKNOWN";
            case "blood_sugar" -> numeric.compareTo(new BigDecimal("140")) > 0 ? "HIGH" : "UNKNOWN";
            case "cholesterol" -> numeric.compareTo(new BigDecimal("200")) >= 0 ? "HIGH" : "UNKNOWN";
            case "ldl" -> numeric.compareTo(new BigDecimal("100")) >= 0 ? "HIGH" : "UNKNOWN";
            case "hdl" -> numeric.compareTo(new BigDecimal("40")) < 0 ? "LOW" : "UNKNOWN";
            case "triglycerides" -> numeric.compareTo(new BigDecimal("150")) >= 0 ? "HIGH" : "UNKNOWN";
            case "creatinine" -> numeric.compareTo(new BigDecimal("1.3")) > 0 ? "HIGH" : "UNKNOWN";
            case "egfr" -> numeric.compareTo(new BigDecimal("60")) < 0 ? "LOW" : "UNKNOWN";
            default -> "UNKNOWN";
        };
    }

    private String displayTestName(String canonicalKey, String fallback) {
        if (hasText(fallback)) {
            return fallback.trim();
        }
        if (!hasText(canonicalKey)) {
            return "Lab Result";
        }
        return switch (canonicalKey) {
            case "hba1c" -> "HbA1c";
            case "estimated_average_glucose" -> "Estimated Average Glucose";
            case "blood_sugar" -> "Random Blood Sugar";
            case "cholesterol" -> "Total Cholesterol";
            case "ldl" -> "LDL Cholesterol";
            case "hdl" -> "HDL Cholesterol";
            case "triglycerides" -> "Triglycerides";
            case "hemoglobin" -> "Hemoglobin";
            case "creatinine" -> "Creatinine";
            case "egfr" -> "eGFR";
            case "crp" -> "CRP";
            case "alt" -> "ALT";
            case "ast" -> "AST";
            default -> fallback;
        };
    }

    private String canonicalLabKey(String raw) {
        if (!hasText(raw)) {
            return null;
        }
        String normalized = slug(raw);
        if (containsAny(normalized, "hba1c", "a1c", "glycated_hemoglobin")) return "hba1c";
        if (containsAny(normalized, "estimated_average_glucose", "eag")) return "estimated_average_glucose";
        if (containsAny(normalized, "random_blood_sugar", "blood_sugar", "glucose", "rbs")) return "blood_sugar";
        if (containsAny(normalized, "hemoglobin")) return "hemoglobin";
        if (containsAny(normalized, "creatinine", "serum_creatinine")) return "creatinine";
        if (containsAny(normalized, "egfr", "estimated_gfr", "estimated_glomerular_filtration_rate")) return "egfr";
        if (containsAny(normalized, "crp", "c_reactive_protein")) return "crp";
        if (containsAny(normalized, "alt", "sgpt", "alanine_aminotransferase")) return "alt";
        if (containsAny(normalized, "ast", "sgot", "aspartate_aminotransferase")) return "ast";
        if (containsAny(normalized, "ldl")) return "ldl";
        if (containsAny(normalized, "hdl")) return "hdl";
        if (containsAny(normalized, "triglycerides", "triglyceride")) return "triglycerides";
        if (containsAny(normalized, "total_cholesterol", "cholesterol")) return "cholesterol";
        return null;
    }

    private String[] evidenceLabelsFor(String canonicalKey) {
        return switch (canonicalKey) {
            case "hba1c" -> new String[]{"HbA1c", "A1c", "Glycated Hemoglobin"};
            case "estimated_average_glucose" -> new String[]{"Estimated Average Glucose", "Average Glucose", "EAG"};
            case "blood_sugar" -> new String[]{"Random Blood Sugar", "Blood Sugar", "Glucose", "RBS"};
            case "cholesterol" -> new String[]{"Total Cholesterol", "Cholesterol"};
            case "ldl" -> new String[]{"LDL Cholesterol", "LDL"};
            case "hdl" -> new String[]{"HDL Cholesterol", "HDL"};
            case "triglycerides" -> new String[]{"Triglycerides"};
            case "hemoglobin" -> new String[]{"Hemoglobin"};
            case "creatinine" -> new String[]{"Creatinine", "Serum Creatinine"};
            case "egfr" -> new String[]{"eGFR", "Estimated GFR", "Estimated Glomerular Filtration Rate"};
            case "crp" -> new String[]{"CRP", "C-Reactive Protein", "C Reactive Protein"};
            case "alt" -> new String[]{"ALT", "SGPT", "Alanine Aminotransferase"};
            case "ast" -> new String[]{"AST", "SGOT", "Aspartate Aminotransferase"};
            default -> new String[]{canonicalKey};
        };
    }

    private String extractLabelBoundValue(String text, String... labels) {
        if (!hasText(text) || labels == null || labels.length == 0) {
            return null;
        }
        String labelPattern = java.util.Arrays.stream(labels)
                .filter(this::hasText)
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));
        Pattern pattern = Pattern.compile("(?i)\\b(?:" + labelPattern + ")\\b[^\\d\\n]{0,24}([<>]?\\s*\\d+(?:\\.\\d+)?)");
        for (String line : text.split("\\R")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private String decimalFrom(String text) {
        if (!hasText(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(-?\\d+\\.\\d+)").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String numberFrom(String text) {
        if (!hasText(text)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private BigDecimal parseNumber(String text) {
        String numeric = numberFrom(text);
        if (!hasText(numeric)) {
            return null;
        }
        try {
            return new BigDecimal(numeric);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String canonicalUnit(String unit) {
        String normalized = unit.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("mg/dl")) {
            return "mg/dL";
        }
        if (normalized.contains("%")) {
            return "%";
        }
        return unit.trim();
    }

    private String extractReferenceRangeFromEvidence(String evidenceText, String value, String unit, String flag) {
        if (!hasText(evidenceText)) {
            return null;
        }
        String normalized = evidenceText.trim();
        String valueToken = firstHasText(value, "");
        String unitToken = firstHasText(unit, "");
        String afterValue = normalized;
        if (hasText(valueToken)) {
            int index = normalized.toLowerCase(Locale.ROOT).indexOf(valueToken.toLowerCase(Locale.ROOT));
            if (index >= 0) {
                afterValue = normalized.substring(index + valueToken.length()).trim();
            }
        }
        if (hasText(unitToken) && afterValue.toLowerCase(Locale.ROOT).startsWith(unitToken.toLowerCase(Locale.ROOT))) {
            afterValue = afterValue.substring(unitToken.length()).trim();
        }
        if (hasText(flag) && afterValue.toUpperCase(Locale.ROOT).endsWith(flag)) {
            afterValue = afterValue.substring(0, afterValue.length() - flag.length()).trim();
        }
        return afterValue.isBlank() ? null : afterValue;
    }

    private boolean rawValueLooksLikeEvidence(String canonicalKey, String rawValue) {
        if (!hasText(canonicalKey) || !hasText(rawValue)) {
            return false;
        }
        String normalized = rawValue.toLowerCase(Locale.ROOT);
        if (startsWithAny(normalized, "review", "discuss", "recommend", "consider", "possible abnormal finding detected")) {
            return false;
        }
        for (String label : evidenceLabelsFor(canonicalKey)) {
            if (hasText(label) && normalized.contains(label.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void traceOcrExtraction(ClinicalDocumentEntity document, ClinicalDocumentTextExtractionResult textResult) {
        if (!log.isInfoEnabled() || document == null || textResult == null) {
            return;
        }
        String ocrText = firstHasText(textResult.text(), "");
        log.info("[AI-DOC-PIPELINE-TRACE] documentId={} stage=OCR provider={} ocrChars={} sanitizedPreview={} labLines={}",
                document.getId(),
                textResult.provider(),
                ocrText.length(),
                sanitizePreview(ocrText),
                extractDetectedLabLines(ocrText));
    }

    private void traceAiResponse(ClinicalDocumentEntity document, AiDraftResponse response) {
        if (!log.isInfoEnabled() || document == null || response == null) {
            return;
        }
        Map<String, Object> structuredData = response.structuredData() == null ? Map.of() : response.structuredData();
        log.info("[AI-DOC-PIPELINE-TRACE] documentId={} stage=AI_RESPONSE provider={} model={} parseStatus={} keysPresent={} labRelatedFields={} suggestedActionsCount={} summaryPresent={}",
                document.getId(),
                response.provider(),
                response.model(),
                "VALID",
                new ArrayList<>(structuredData.keySet()),
                collectLabRelatedFields(structuredData),
                response.suggestedActions() == null ? 0 : response.suggestedActions().size(),
                hasText(response.draft()) || hasText(stringValue(structuredData.get("summary"))) || hasText(stringValue(structuredData.get("answer"))));
        log.info("[AI-DOC-PIPELINE-TRACE] stage=AI_STRUCTURED_JSON documentId={} jsonKeys={} labResultsRaw={} factualFindingsRaw={} summaryPresent={} recommendationsPresent={}",
                document.getId(),
                new ArrayList<>(structuredData.keySet()),
                summarizeJsonField(rawLabResults(structuredData)),
                summarizeJsonField(structuredData.get("factualFindings")),
                hasText(stringValue(structuredData.get("summary"))) || hasText(stringValue(structuredData.get("answer"))) || hasText(response.draft()),
                structuredData.get("recommendations") != null || structuredData.get("suggestedActions") != null || (response.suggestedActions() != null && !response.suggestedActions().isEmpty()));
    }

    @SuppressWarnings("unchecked")
    private Object rawLabResults(Map<String, Object> structuredData) {
        if (structuredData == null || structuredData.isEmpty()) {
            return null;
        }
        Object factualFindings = structuredData.get("factualFindings");
        if (factualFindings instanceof Map<?, ?> factualMap && factualMap.get("labResults") != null) {
            return factualMap.get("labResults");
        }
        List<Map<String, Object>> classificationRows = extractClassificationDetailLabResults(structuredData);
        if (!classificationRows.isEmpty()) {
            return classificationRows;
        }
        return firstNonNull(structuredData.get("labResults"), structuredData.get("labValues"), structuredData.get("labs"), structuredData.get("results"));
    }

    private List<String> extractDetectedLabLines(String ocrText) {
        if (!hasText(ocrText)) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : ocrText.split("\\R")) {
            String normalized = line.toLowerCase(Locale.ROOT);
            if (containsAny(normalized, "hba1c", "hb a1c", "a1c", "glycated hemoglobin", "glycosylated hemoglobin", "hemoglobin", "random blood sugar", "glucose", "cholesterol", "hdl", "ldl", "triglycerides", "creatinine", "egfr", "estimated gfr", "crp", "c-reactive protein", "alt", "ast", "sgpt", "sgot")) {
                lines.add(summarizeText(line));
            }
        }
        return lines.stream().distinct().toList();
    }

    private List<String> collectLabRelatedFields(Map<String, Object> structuredData) {
        if (structuredData == null || structuredData.isEmpty()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        collectLabRelatedFields(structuredData, "", fields);
        return fields.stream().distinct().toList();
    }

    private void collectLabRelatedFields(Object value, String path, List<String> target) {
        if (value == null || target == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = stringValue(entry.getKey());
                String childPath = hasText(path) ? path + "." + key : key;
                if (containsAny(childPath == null ? null : childPath.toLowerCase(Locale.ROOT), "lab", "hba1c", "a1c", "glucose", "sugar", "cholesterol", "ldl", "hdl", "triglyceride", "creatinine", "egfr", "crp", "alt", "ast")) {
                    target.add(childPath);
                }
                collectLabRelatedFields(entry.getValue(), childPath, target);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object item : iterable) {
                collectLabRelatedFields(item, path + "[" + index++ + "]", target);
            }
        }
    }

    private boolean isBlockedNarrativeLabSource(String text) {
        if (!hasText(text)) {
            return false;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return startsWithAny(normalized, "review", "discuss", "recommend", "consider", "possible abnormal finding detected", "suggestedactions", "answer")
                || normalized.contains("recommend lifestyle")
                || normalized.contains("follow up")
                || normalized.contains("follow-up")
                || normalized.contains("discuss abnormal lipid profile");
    }

    private String sanitizePreview(String text) {
        String sanitized = summarizeText(text);
        return sanitized == null ? null : sanitized.substring(0, Math.min(1000, sanitized.length()));
    }

    private String summarizeText(String text) {
        if (!hasText(text)) {
            return null;
        }
        String sanitized = text.replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }

    private String summarizeValue(String text) {
        if (!hasText(text)) {
            return null;
        }
        return text.length() <= 64 ? text : text.substring(0, 64);
    }

    private String buildDetailEvidence(String testName, String result, String referenceRange, String flag) {
        List<String> parts = new ArrayList<>();
        if (hasText(testName)) {
            parts.add(testName.trim());
        }
        if (hasText(result)) {
            parts.add(result.trim());
        }
        if (hasText(referenceRange)) {
            parts.add(referenceRange.trim());
        }
        if (hasText(flag)) {
            parts.add(flag.trim());
        }
        return String.join(" ", parts).trim();
    }

    private String extractUnitFromResult(String result) {
        if (!hasText(result)) {
            return null;
        }
        String normalized = result.toLowerCase(Locale.ROOT);
        if (normalized.contains("mg/dl")) {
            return "mg/dL";
        }
        if (normalized.contains("%")) {
            return "%";
        }
        if (normalized.contains("g/dl")) {
            return "g/dL";
        }
        return null;
    }

    private String normalizeFlagLabel(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("HIGH")) {
            return "HIGH";
        }
        if (normalized.contains("LOW")) {
            return "LOW";
        }
        if (normalized.contains("NORMAL")) {
            return "NORMAL";
        }
        return normalized;
    }

    private String normalizeConfidence(Object rawConfidence, BigDecimal responseConfidence) {
        String fromStructured = stringValue(rawConfidence);
        if (hasText(fromStructured)) {
            return fromStructured.trim().toUpperCase(Locale.ROOT);
        }
        if (responseConfidence == null) {
            return null;
        }
        BigDecimal percent = responseConfidence.multiply(new BigDecimal("100"));
        if (percent.compareTo(new BigDecimal("90")) >= 0) {
            return "VERY_HIGH";
        }
        if (percent.compareTo(new BigDecimal("75")) >= 0) {
            return "HIGH";
        }
        if (percent.compareTo(new BigDecimal("60")) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String summarizeJsonField(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return summarizeText(objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            return summarizeText(String.valueOf(value));
        }
    }

    private String findLine(String text, String... needles) {
        if (!hasText(text) || needles == null) {
            return null;
        }
        for (String line : text.split("\\R")) {
            String normalized = line.toLowerCase(Locale.ROOT);
            for (String needle : needles) {
                if (hasText(needle) && normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                    return line.trim();
                }
            }
        }
        return null;
    }

    private boolean startsWithAny(String text, String... prefixes) {
        if (!hasText(text) || prefixes == null) {
            return false;
        }
        for (String prefix : prefixes) {
            if (hasText(prefix) && text.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String value, String... needles) {
        if (!hasText(value) || needles == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (hasText(needle) && normalized.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String slug(String value) {
        return value == null ? null : value.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "").toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> parseStructuredJson(String structuredJson) {
        if (!StringUtils.hasText(structuredJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(structuredJson, new TypeReference<>() {});
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
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
                resolveActorUsername(tenantId, actorAppUserId),
                forceNewJob
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
        if (forceNewJob) {
            document.markAiRetryRequested(saved.getId(), actorAppUserId, "Queued document AI reprocess");
        }
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

    private String firstHasText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String latestHbA1cValue(UUID tenantId, UUID patientId) {
        try {
            var profile = longitudinalMemoryService.buildProfile(tenantId, patientId);
            return profile == null || profile.latestHbA1c() == null
                    ? null
                    : profile.latestHbA1c().valueText();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private int historyCount(UUID tenantId, UUID patientId) {
        try {
            var profile = longitudinalMemoryService.buildProfile(tenantId, patientId);
            return profile == null || profile.history() == null ? 0 : profile.history().size();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
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
            UUID documentId,
            boolean reprocessRequested
    ) {
        private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

        static ProcessingRequestContext capture(UUID tenantId,
                                                UUID patientId,
                                                UUID documentId,
                                                UUID actorAppUserId,
                                                RequestContext requestContext,
                                                String actorUsername,
                                                boolean reprocessRequested) {
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
                    documentId,
                    reprocessRequested
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
                        uuidValue(rawContext.get("documentId"), job.getDocumentId()),
                        booleanValue(rawContext.get("reprocess"))
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
            payload.put("reprocess", reprocessRequested);
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
                    job.getDocumentId(),
                    false
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

        private static boolean booleanValue(Object value) {
            if (value == null) {
                return false;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            return Boolean.parseBoolean(value.toString());
        }
    }
}
