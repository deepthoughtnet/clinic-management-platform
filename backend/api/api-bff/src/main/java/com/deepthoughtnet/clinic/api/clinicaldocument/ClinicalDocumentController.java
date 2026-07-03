package com.deepthoughtnet.clinic.api.clinicaldocument;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.service.ClinicalDocumentAiExtractionService;
import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.AiExtractionReviewRequest;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalDocumentResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.DocumentDownloadUrlResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.PatientTimelineItemResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentPatchCommand;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentUploadCommand;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api")
public class ClinicalDocumentController {
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final ClinicalDocumentService documentService;
    private final ClinicalDocumentAiExtractionService aiExtractionService;
    private final PatientService patientService;
    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public ClinicalDocumentController(
            ClinicalDocumentService documentService,
            ClinicalDocumentAiExtractionService aiExtractionService,
            PatientService patientService,
            ConsultationService consultationService,
            PrescriptionService prescriptionService,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService
    ) {
        this.documentService = documentService;
        this.aiExtractionService = aiExtractionService;
        this.patientService = patientService;
        this.consultationService = consultationService;
        this.prescriptionService = prescriptionService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping("/patients/{patientId}/documents")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public List<ClinicalDocumentResponse> listPatientDocuments(
            @PathVariable UUID patientId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) LocalDate reportDateFrom,
            @RequestParam(required = false) LocalDate reportDateTo,
            @RequestParam(required = false) String uploadSource,
            @RequestParam(required = false) UUID consultationId,
            @RequestParam(required = false) String search
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        ClinicalDocumentType type = parseTypeOrNull(documentType);
        return documentService.listByPatient(tenantId, patientId, type, reportDateFrom, reportDateTo, uploadSource, consultationId, search)
                .stream().map(this::toResponse).toList();
    }

    @PostMapping("/patients/{patientId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.upload') or @permissionChecker.hasPermission('clinic.document.upload')")
    public ClinicalDocumentResponse uploadPatientDocument(
            @PathVariable UUID patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam("title") String title,
            @RequestParam(required = false) LocalDate reportDate,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) UUID consultationId,
            @RequestParam(required = false) String uploadSource,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) String sourceEntityId,
            @RequestParam(required = false) String visibility
    ) throws Exception {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        ClinicalDocumentRecord record = documentService.upload(new ClinicalDocumentUploadCommand(
                tenantId,
                patientId,
                consultationId,
                actorAppUserId,
                parseType(documentType),
                title,
                reportDate,
                uploadSource,
                sourceModule,
                sourceEntityId,
                visibility,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                notes
        ));
        aiExtractionService.queueExtraction(tenantId, record.id(), actorAppUserId);
        return toResponse(record);
    }

    @PatchMapping("/patients/{patientId}/documents/{documentId}")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.manage') or @permissionChecker.hasPermission('clinic.document.upload')")
    public ClinicalDocumentResponse patchPatientDocument(
            @PathVariable UUID patientId,
            @PathVariable UUID documentId,
            @Valid @RequestBody ClinicalDocumentPatchRequest request
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        ClinicalDocumentRecord record = documentService.patch(
                tenantId,
                documentId,
                new ClinicalDocumentPatchCommand(
                        parseTypeOrNull(request.documentType()),
                        request.title(),
                        request.description(),
                        request.reportDate(),
                        request.visibility(),
                        request.verificationStatus()
                ),
                actorAppUserId
        );
        return toResponse(record);
    }

    @DeleteMapping("/patients/{patientId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.delete') or @permissionChecker.hasPermission('patient.document.manage') or @permissionChecker.hasPermission('clinic.document.upload')")
    public void deletePatientDocument(@PathVariable UUID patientId, @PathVariable UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        documentService.delete(tenantId, documentId, RequestContextHolder.require().appUserId());
    }

    @GetMapping("/patients/{patientId}/documents/{documentId}/download")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public DocumentDownloadUrlResponse download(@PathVariable UUID patientId, @PathVariable UUID documentId) {
        return accessUrl(patientId, documentId);
    }

    @GetMapping("/patients/{patientId}/documents/{documentId}/view")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public DocumentDownloadUrlResponse view(@PathVariable UUID patientId, @PathVariable UUID documentId) {
        return accessUrl(patientId, documentId);
    }

    @GetMapping("/patient-documents/{documentId}/download-url")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public DocumentDownloadUrlResponse legacyDownloadUrl(@PathVariable UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId());
        return new DocumentDownloadUrlResponse(documentService.downloadUrl(tenantId, documentId, DOWNLOAD_TTL), String.valueOf(DOWNLOAD_TTL.toSeconds()));
    }

    @PostMapping("/patient-documents/{documentId}/ai-extraction/review")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.update') or @permissionChecker.hasPermission('consultation.complete')")
    public ClinicalDocumentResponse reviewAiExtraction(@PathVariable UUID documentId, @Valid @RequestBody AiExtractionReviewRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        ClinicalDocumentRecord record = aiExtractionService.review(
                tenantId,
                documentId,
                actorAppUserId,
                request.approved(),
                request.saveToPatientHistory(),
                request.reviewNotes(),
                request.acceptedStructuredJson(),
                request.overrideReason(),
                request.editedSummary()
        );
        return toResponse(record);
    }

    @PostMapping("/patient-documents/{documentId}/ai-extraction/reprocess")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.update') or @permissionChecker.hasPermission('consultation.complete')")
    public ClinicalDocumentResponse reprocessAiExtraction(@PathVariable UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        aiExtractionService.queueExtraction(tenantId, documentId, actorAppUserId);
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        return toResponse(record);
    }

    @GetMapping("/patient-documents/{documentId}")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public ClinicalDocumentResponse getDocument(@PathVariable UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId());
        return toResponse(record);
    }

    @GetMapping("/patients/{patientId}/timeline")
    @PreAuthorize("@permissionChecker.hasPermission('patient.read')")
    public List<PatientTimelineItemResponse> patientTimeline(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        List<PatientTimelineItemResponse> items = new ArrayList<>();
        documentService.listByPatient(tenantId, patientId).forEach(doc -> items.add(new PatientTimelineItemResponse(
                doc.id().toString(),
                "DOCUMENT",
                documentTypeLabel(doc.documentType()),
                documentTimelineSubtitle(doc),
                doc.createdAt().toString(),
                doc.verificationStatus(),
                doc.documentType().name(),
                doc.id().toString(),
                doc.consultationId() == null ? null : doc.consultationId().toString(),
                null
        )));
        consultationService.listByPatient(tenantId, patientId).forEach(row -> items.add(new PatientTimelineItemResponse(
                row.id().toString(), "CONSULTATION", row.diagnosis() == null || row.diagnosis().isBlank() ? "Consultation" : row.diagnosis(), consultationTimelineSubtitle(row), row.createdAt().toString(), row.status().name(), null, null, row.id().toString(), null
        )));
        prescriptionService.listByPatient(tenantId, patientId).forEach(row -> items.add(new PatientTimelineItemResponse(
                row.id().toString(), "PRESCRIPTION", row.prescriptionNumber(), prescriptionTimelineSubtitle(row), row.createdAt().toString(), row.status().name(), "PRESCRIPTION", null, row.consultationId().toString(), row.id().toString()
        )));
        return items.stream().sorted(Comparator.comparing(PatientTimelineItemResponse::occurredAt).reversed()).limit(100).toList();
    }

    private DocumentDownloadUrlResponse accessUrl(UUID patientId, UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        if (!record.patientId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId());
        return new DocumentDownloadUrlResponse(documentService.downloadUrl(tenantId, documentId, DOWNLOAD_TTL), String.valueOf(DOWNLOAD_TTL.toSeconds()));
    }

    private void requirePatientExistsAndVisible(UUID tenantId, UUID patientId) {
        patientService.findById(tenantId, patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, patientId);
    }

    private ClinicalDocumentType parseTypeOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseType(value);
    }

    private ClinicalDocumentType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Document type is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LAB_REPORT", "EXTERNAL_LAB_REPORT" -> ClinicalDocumentType.EXTERNAL_LAB_REPORT;
            case "X_RAY", "MRI_CT", "RADIOLOGY_REPORT" -> ClinicalDocumentType.RADIOLOGY_REPORT;
            case "REFERRAL", "REFERRAL_LETTER" -> ClinicalDocumentType.REFERRAL_LETTER;
            case "DISCHARGE_SUMMARY" -> ClinicalDocumentType.DISCHARGE_SUMMARY;
            case "PRESCRIPTION", "OLD_PRESCRIPTION" -> ClinicalDocumentType.OLD_PRESCRIPTION;
            case "INTERNAL_LAB_REPORT" -> ClinicalDocumentType.INTERNAL_LAB_REPORT;
            case "INSURANCE", "INSURANCE_DOCUMENT" -> ClinicalDocumentType.INSURANCE_DOCUMENT;
            case "IDENTITY_DOCUMENT" -> ClinicalDocumentType.IDENTITY_DOCUMENT;
            case "OTHER", "ATTACHMENT", "VACCINATION" -> ClinicalDocumentType.OTHER;
            default -> ClinicalDocumentType.valueOf(normalized);
        };
    }

    private String documentTypeLabel(ClinicalDocumentType type) {
        return switch (type) {
            case EXTERNAL_LAB_REPORT, LAB_REPORT -> "External Lab Report";
            case INTERNAL_LAB_REPORT -> "Internal Lab Report";
            case RADIOLOGY_REPORT, X_RAY, MRI_CT -> "Radiology Report";
            case REFERRAL_LETTER, REFERRAL -> "Referral Letter";
            case DISCHARGE_SUMMARY -> "Discharge Summary";
            case OLD_PRESCRIPTION, PRESCRIPTION -> "Old Prescription";
            case INSURANCE_DOCUMENT, INSURANCE -> "Insurance Document";
            case IDENTITY_DOCUMENT -> "Identity Document";
            case VACCINATION -> "Vaccination";
            case OTHER, ATTACHMENT -> "Other";
        };
    }

    private String documentTimelineSubtitle(ClinicalDocumentRecord record) {
        StringBuilder subtitle = new StringBuilder();
        subtitle.append(record.title() == null || record.title().isBlank() ? record.originalFilename() : record.title());
        subtitle.append(" uploaded by ").append(record.uploadedByName());
        if (record.uploadSource() != null && !record.uploadSource().isBlank()) {
            subtitle.append(" • ").append(record.uploadSource());
        }
        if (record.sourceModule() != null && !record.sourceModule().isBlank()) {
            subtitle.append(" • ").append(record.sourceModule());
        }
        if (record.aiExtractionStatus() != null && !record.aiExtractionStatus().isBlank()) {
            subtitle.append(" • AI ").append(record.aiExtractionStatus());
        }
        if (record.ocrStatus() != null && !record.ocrStatus().isBlank()) {
            subtitle.append(" • OCR ").append(record.ocrStatus());
        }
        return subtitle.toString();
    }

    private String consultationTimelineSubtitle(com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord record) {
        StringBuilder subtitle = new StringBuilder(record.status().name());
        if (record.followUpDate() != null) {
            subtitle.append(" | Follow-up ").append(record.followUpDate());
        }
        return subtitle.toString();
    }

    private String prescriptionTimelineSubtitle(com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord record) {
        StringBuilder subtitle = new StringBuilder("v").append(record.versionNumber() == null ? 1 : record.versionNumber());
        subtitle.append(" | ").append(record.status().name());
        if (record.correctionReason() != null && !record.correctionReason().isBlank()) {
            subtitle.append(" | ").append(record.correctionReason());
        }
        if (record.followUpDate() != null) {
            subtitle.append(" | Follow-up ").append(record.followUpDate());
        }
        return subtitle.toString();
    }

    private ClinicalDocumentResponse toResponse(ClinicalDocumentRecord record) {
        return new ClinicalDocumentResponse(
                record.id().toString(),
                record.patientId().toString(),
                record.consultationId() == null ? null : record.consultationId().toString(),
                record.sourceModule(),
                record.sourceEntityId(),
                record.uploadedByUserId().toString(),
                record.uploadedByName(),
                record.documentType().name(),
                record.title(),
                record.description(),
                record.reportDate() == null ? null : record.reportDate().toString(),
                record.uploadSource(),
                record.originalFilename(),
                record.mediaType(),
                record.sizeBytes(),
                record.checksumSha256(),
                record.storageBucket(),
                record.storageKey(),
                record.visibility(),
                record.verificationStatus(),
                record.ocrStatus(),
                record.aiIndexStatus(),
                record.aiExtractionStatus(),
                record.aiExtractionProvider(),
                record.aiExtractionModel(),
                record.aiExtractionConfidence(),
                record.aiExtractionSummary(),
                record.aiExtractionStructuredJson(),
                record.aiExtractionReviewNotes(),
                record.aiExtractionAcceptedJson(),
                record.aiExtractionOverrideReason(),
                record.aiExtractionReviewedByAppUserId() == null ? null : record.aiExtractionReviewedByAppUserId().toString(),
                record.aiExtractionReviewedAt() == null ? null : record.aiExtractionReviewedAt().toString(),
                record.active(),
                record.createdAt().toString(),
                record.updatedAt().toString()
        );
    }

    public record ClinicalDocumentPatchRequest(
            String documentType,
            String title,
            String description,
            LocalDate reportDate,
            String visibility,
            String verificationStatus
    ) {
    }
}
