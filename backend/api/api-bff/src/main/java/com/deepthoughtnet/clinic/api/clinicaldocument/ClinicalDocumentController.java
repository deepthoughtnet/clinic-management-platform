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
import com.deepthoughtnet.clinic.vaccination.service.VaccinationService;
import com.deepthoughtnet.clinic.vaccination.service.model.PatientVaccinationRecord;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final VaccinationService vaccinationService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public ClinicalDocumentController(
            ClinicalDocumentService documentService,
            ClinicalDocumentAiExtractionService aiExtractionService,
            PatientService patientService,
            ConsultationService consultationService,
            PrescriptionService prescriptionService,
            VaccinationService vaccinationService,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService
    ) {
        this.documentService = documentService;
        this.aiExtractionService = aiExtractionService;
        this.patientService = patientService;
        this.consultationService = consultationService;
        this.prescriptionService = prescriptionService;
        this.vaccinationService = vaccinationService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping("/patients/{patientId}/documents")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read') or @doctorAssignmentSecurityService.isDoctor()")
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
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read') or @doctorAssignmentSecurityService.isDoctor()")
    public ResponseEntity<byte[]> download(@PathVariable UUID patientId, @PathVariable UUID documentId) {
        return streamedDocument(patientId, documentId, false);
    }

    @GetMapping("/patients/{patientId}/documents/{documentId}/view")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read') or @doctorAssignmentSecurityService.isDoctor()")
    public ResponseEntity<byte[]> view(@PathVariable UUID patientId, @PathVariable UUID documentId) {
        return streamedDocument(patientId, documentId, true);
    }

    @GetMapping("/patient-documents/{documentId}/download-url")
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read') or @doctorAssignmentSecurityService.isDoctor()")
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
    @PreAuthorize("@permissionChecker.hasPermission('patient.document.read') or @permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read') or @doctorAssignmentSecurityService.isDoctor()")
    public ClinicalDocumentResponse getDocument(@PathVariable UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId());
        return toResponse(record);
    }

    @GetMapping("/patients/{patientId}/timeline")
    @PreAuthorize("@permissionChecker.hasPermission('patient.read') or @doctorAssignmentSecurityService.isDoctor()")
    public List<PatientTimelineItemResponse> patientTimeline(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        List<PatientTimelineItemResponse> items = new ArrayList<>();
        documentService.listByPatient(tenantId, patientId).forEach(doc -> items.add(new PatientTimelineItemResponse(
                doc.id().toString(),
                "DOCUMENT",
                documentTimelineTitle(doc),
                documentTimelineSubtitle(doc),
                doc.createdAt().toString(),
                documentTimelineStatus(doc),
                doc.documentType().name(),
                doc.id().toString(),
                doc.consultationId() == null ? null : doc.consultationId().toString(),
                null
        )));
        consultationService.listByPatient(tenantId, patientId).forEach(row -> items.add(new PatientTimelineItemResponse(
                row.id().toString(), "CONSULTATION", consultationTimelineTitle(row), consultationTimelineSubtitle(row), row.createdAt().toString(), consultationTimelineStatus(row), null, null, row.id().toString(), null
        )));
        prescriptionService.listByPatient(tenantId, patientId).forEach(row -> items.add(new PatientTimelineItemResponse(
                row.id().toString(), "PRESCRIPTION", prescriptionTimelineTitle(row), prescriptionTimelineSubtitle(row), row.createdAt().toString(), prescriptionTimelineStatus(row), "PRESCRIPTION", null, row.consultationId().toString(), row.id().toString()
        )));
        vaccinationService.listByPatient(tenantId, patientId).forEach(row -> items.add(new PatientTimelineItemResponse(
                row.id().toString(),
                "VACCINATION",
                vaccinationTimelineTitle(row),
                vaccinationTimelineSubtitle(row),
                row.createdAt().toString(),
                "RECORDED",
                "VACCINATION",
                null,
                null,
                null
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

    private ResponseEntity<byte[]> streamedDocument(UUID patientId, UUID documentId, boolean inline) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        if (!record.patientId().equals(patientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId());
        byte[] bytes = documentService.downloadBytes(tenantId, documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(bytes.length);
        headers.setContentDisposition(ContentDisposition.builder(inline ? "inline" : "attachment")
                .filename(record.originalFilename() == null || record.originalFilename().isBlank() ? "document.pdf" : record.originalFilename())
                .build());
        return ResponseEntity.ok().headers(headers).body(bytes);
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
            case EXTERNAL_LAB_REPORT -> "External Lab Report";
            case LAB_REPORT -> "Lab Report";
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
        if (isPublishedLabDocument(record)) {
            return "Published • Available";
        }
        StringBuilder subtitle = new StringBuilder();
        String documentLabel = record.title() == null || record.title().isBlank() ? record.originalFilename() : record.title();
        subtitle.append(documentLabel == null || documentLabel.isBlank() ? "Clinical document" : documentLabel);
        String uploadedByName = record.uploadedByName();
        if (uploadedByName != null && !uploadedByName.isBlank()) {
            subtitle.append(" uploaded by ").append(uploadedByName);
        }
        if (record.uploadSource() != null && !record.uploadSource().isBlank()) {
            subtitle.append(" • ").append(record.uploadSource());
        }
        if (record.sourceModule() != null && !record.sourceModule().isBlank()) {
            if (!isLaboratorySource(record.sourceModule()) && !"DOCUMENT".equalsIgnoreCase(record.sourceModule())) {
                subtitle.append(" • ").append(record.sourceModule());
            }
        }
        String verificationStatus = documentTimelineStatus(record);
        if (verificationStatus != null && !verificationStatus.isBlank()) {
            subtitle.append(" • ").append(verificationStatus);
        }
        return subtitle.toString();
    }

    private String documentTimelineTitle(ClinicalDocumentRecord record) {
        if (record == null) {
            return "Document";
        }
        if (isPublishedLabDocument(record)) {
            return "Laboratory Report Published";
        }
        return documentTypeLabel(record.documentType());
    }

    private String documentTimelineStatus(ClinicalDocumentRecord record) {
        if (record == null) {
            return null;
        }
        if (isPublishedLabDocument(record)) {
            return "Published";
        }
        String verificationStatus = documentBusinessStatusLabel(record.verificationStatus());
        if (verificationStatus != null) {
            return verificationStatus;
        }
        return documentBusinessStatusLabel(record.visibility());
    }

    private String consultationTimelineTitle(com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord record) {
        if (record == null) {
            return "Consultation";
        }
        return record.status() == com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus.COMPLETED
                ? "Consultation Completed"
                : "Consultation";
    }

    private String consultationTimelineStatus(com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord record) {
        if (record == null) {
            return null;
        }
        return switch (record.status()) {
            case COMPLETED -> "Completed";
            case DRAFT -> "Draft";
            case CANCELLED -> "Cancelled";
        };
    }

    private boolean isPublishedLabDocument(ClinicalDocumentRecord record) {
        return record != null
                && record.documentType() == ClinicalDocumentType.LAB_REPORT
                && isLaboratorySource(record.sourceModule())
                && isPatientVisiblePublished(record);
    }

    private boolean isLaboratorySource(String sourceModule) {
        if (sourceModule == null || sourceModule.isBlank()) {
            return false;
        }
        String normalized = sourceModule.trim().toUpperCase(Locale.ROOT);
        return "LAB".equals(normalized) || "LABORATORY".equals(normalized);
    }

    private boolean isPatientVisiblePublished(ClinicalDocumentRecord record) {
        return record != null
                && ("PATIENT_VISIBLE".equalsIgnoreCase(record.visibility())
                || "PUBLISHED".equalsIgnoreCase(record.verificationStatus())
                || "AVAILABLE".equalsIgnoreCase(record.verificationStatus()));
    }

    private String documentBusinessStatusLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLISHED" -> "Published";
            case "AVAILABLE" -> "Available";
            case "PATIENT_VISIBLE" -> "Available";
            case "INTERNAL_ONLY" -> null;
            case "VERIFIED" -> "Verified";
            case "UNVERIFIED" -> "Unverified";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            case "REVIEW_REQUIRED" -> "Review required";
            case "PENDING", "UNDER_REVIEW" -> "Under review";
            case "COMPLETED", "DONE" -> "Completed";
            case "PROCESSING" -> "In progress";
            default -> null;
        };
    }

    private String consultationTimelineSubtitle(com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord record) {
        StringBuilder subtitle = new StringBuilder(record.status().name());
        if (record.followUpDate() != null) {
            subtitle.append(" | Follow-up ").append(record.followUpDate());
        }
        return subtitle.toString();
    }

    private String prescriptionTimelineTitle(com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord record) {
        if (record == null) {
            return "Prescription";
        }
        return record.status() == com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus.FINALIZED
                ? "Prescription Generated"
                : "Prescription";
    }

    private String prescriptionTimelineStatus(com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord record) {
        if (record == null) {
            return null;
        }
        return switch (record.status()) {
            case FINALIZED -> "Generated";
            case DRAFT -> "Draft";
            case SUPERSEDED -> "Superseded";
            case CANCELLED -> "Cancelled";
            default -> record.status().name();
        };
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

    private String vaccinationTimelineTitle(PatientVaccinationRecord record) {
        String vaccine = record.vaccineName() == null || record.vaccineName().isBlank() ? "Vaccination" : record.vaccineName();
        return record.doseNumber() == null ? vaccine : vaccine + " dose " + record.doseNumber();
    }

    private String vaccinationTimelineSubtitle(PatientVaccinationRecord record) {
        List<String> parts = new ArrayList<>();
        if (record.givenDate() != null) {
            parts.add("Given " + record.givenDate());
        }
        if (record.nextDueDate() != null) {
            parts.add("Next due " + record.nextDueDate());
        }
        if (record.batchNumber() != null && !record.batchNumber().isBlank()) {
            parts.add("Batch " + record.batchNumber());
        }
        if (record.inventoryBatchManufacturer() != null && !record.inventoryBatchManufacturer().isBlank()) {
            parts.add(record.inventoryBatchManufacturer());
        }
        return parts.isEmpty() ? "Vaccination recorded" : String.join(" • ", parts);
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
