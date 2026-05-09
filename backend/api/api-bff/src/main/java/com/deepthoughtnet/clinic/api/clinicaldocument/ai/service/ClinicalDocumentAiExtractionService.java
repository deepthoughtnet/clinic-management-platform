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
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.patient.service.model.PatientRecord;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiEvidenceReference;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProductCode;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    private static final Pattern LAB_PATTERN = Pattern.compile(
            "(?i)\\b(hemoglobin|hb|glucose|blood sugar|cholesterol|hdl|ldl|triglycerides|bilirubin|alt|ast|alp|alk phos|creatinine)\\b[^\\d\\n]{0,20}([<>]?\\s*\\d+(?:\\.\\d+)?)"
    );

    private final ClinicalAiJobRepository jobRepository;
    private final ClinicalDocumentRepository documentRepository;
    private final ClinicalDocumentService documentService;
    private final ClinicalDocumentTextExtractionService textExtractionService;
    private final AiDoctorCopilotService aiDoctorCopilotService;
    private final ObjectStorageService storageService;
    private final AuditEventPublisher auditEventPublisher;
    private final AgentExecutionLogService agentExecutionLogService;
    private final PatientService patientService;
    private final ObjectMapper objectMapper;
    private final long retryBackoffMs;
    private final int maxAttempts;

    public ClinicalDocumentAiExtractionService(ClinicalAiJobRepository jobRepository,
                                               ClinicalDocumentRepository documentRepository,
                                               ClinicalDocumentService documentService,
                                               ClinicalDocumentTextExtractionService textExtractionService,
                                               AiDoctorCopilotService aiDoctorCopilotService,
                                               ObjectStorageService storageService,
                                               AuditEventPublisher auditEventPublisher,
                                               AgentExecutionLogService agentExecutionLogService,
                                               PatientService patientService,
                                               ObjectMapper objectMapper,
                                               @Value("${clinic.ai.jobs.retryBackoffMs:60000}") long retryBackoffMs,
                                               @Value("${clinic.ai.jobs.maxAttempts:3}") int maxAttempts) {
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.textExtractionService = textExtractionService;
        this.aiDoctorCopilotService = aiDoctorCopilotService;
        this.storageService = storageService;
        this.auditEventPublisher = auditEventPublisher;
        this.agentExecutionLogService = agentExecutionLogService;
        this.patientService = patientService;
        this.objectMapper = objectMapper;
        this.retryBackoffMs = retryBackoffMs <= 0 ? 60000 : retryBackoffMs;
        this.maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    }

    @Transactional
    public ClinicalAiJobEntity queueExtraction(UUID tenantId, UUID documentId, UUID actorAppUserId) {
        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        Optional<ClinicalAiJobEntity> existing = jobRepository.findFirstByTenantIdAndDocumentIdAndJobTypeOrderByCreatedAtDesc(
                tenantId, documentId, ClinicalAiJobType.DOCUMENT_EXTRACTION);
        if (existing.isPresent() && (existing.get().getStatus() == ClinicalAiJobStatus.QUEUED
                || existing.get().getStatus() == ClinicalAiJobStatus.PROCESSING)) {
            return existing.get();
        }
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
                        "documentId", document.getId(),
                        "patientId", document.getPatientId(),
                        "documentType", document.getDocumentType().name()
                ))
        );
        ClinicalAiJobEntity saved = jobRepository.save(job);
        document.markAiExtractionQueued();
        documentRepository.save(document);
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                documentId,
                "clinical.document.ai_extraction.queued",
                actorAppUserId,
                OffsetDateTime.now(),
                "Queued clinical AI extraction job",
                "{\"documentId\":\"%s\"}".formatted(documentId)
        ));
        return saved;
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

        job.markProcessing();
        document.markAiExtractionProcessing("PROCESSING");
        documentRepository.save(document);
        jobRepository.save(job);

        try {
            byte[] bytes = storageService.getObjectBytes(document.getStorageKey());
            ClinicalDocumentTextExtractionResult textResult = textExtractionService.extract(document, bytes);
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
        } catch (RuntimeException ex) {
            boolean retryable = job.getAttemptCount() + 1 < maxAttempts;
            document.markAiExtractionFailed(job.getProvider(), job.getModel(), ex.getMessage());
            documentRepository.save(document);
            job.markFailed(ex.getMessage(), retryable, retryBackoffMs);
            jobRepository.save(job);
            log.warn("Clinical AI extraction failed safely. jobId={}, tenantId={}, documentId={}, retryable={}, error={}",
                    job.getId(), job.getTenantId(), job.getDocumentId(), retryable, ex.getMessage(), ex);
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
        ), reviewerAppUserId);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize clinical AI payload", ex);
        }
    }
}
