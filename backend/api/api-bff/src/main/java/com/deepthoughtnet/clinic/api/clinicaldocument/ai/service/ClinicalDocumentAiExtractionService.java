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
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.ai.orchestration.db.AgentExecutionLogEntity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalDocumentAiExtractionService {
    private static final String ENTITY_TYPE = "CLINICAL_DOCUMENT_AI";

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
        if (existing.isPresent() && existing.get().getStatus() == ClinicalAiJobStatus.QUEUED) {
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

            String resultJson = toJson(response.structuredData());
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

            job.markSucceeded(response.provider(), response.model(), textResult.provider(), confidence, summary, resultJson);
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
            throw ex;
        }
    }

    @Transactional
    public ClinicalDocumentRecord review(UUID tenantId, UUID documentId, UUID reviewerAppUserId, boolean approved, boolean saveToPatientHistory, String reviewNotes) {
        ClinicalDocumentEntity document = documentRepository.findByTenantIdAndId(tenantId, documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        String reviewStatus = approved ? "APPROVED" : "REJECTED";
        document.markAiExtractionReviewed(reviewerAppUserId, reviewNotes, reviewStatus);
        ClinicalDocumentEntity saved = documentRepository.save(document);

        if (approved && saveToPatientHistory) {
            pushToPatientHistory(saved, reviewNotes, reviewerAppUserId);
        }

        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                documentId,
                "clinical.document.ai_extraction.reviewed",
                reviewerAppUserId,
                OffsetDateTime.now(),
                "Reviewed clinical AI extraction",
                "{\"approved\":%s}".formatted(approved)
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

    private Map<String, Object> patientSummary(UUID tenantId, UUID patientId) {
        PatientRecord patient = patientService.findById(tenantId, patientId).orElse(null);
        if (patient == null) {
            return Map.of();
        }
        return Map.of(
                "patientId", patient.id().toString(),
                "patientNumber", patient.patientNumber(),
                "patientName", patient.fullName(),
                "ageYears", patient.ageYears(),
                "gender", patient.gender().name(),
                "mobile", patient.mobile(),
                "allergies", patient.allergies(),
                "existingConditions", patient.existingConditions(),
                "longTermMedications", patient.longTermMedications(),
                "surgicalHistory", patient.surgicalHistory()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize clinical AI payload", ex);
        }
    }
}
