package com.deepthoughtnet.clinic.api.clinicaldocument;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.ClinicalDocumentResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.DocumentDownloadUrlResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.dto.PatientTimelineItemResponse;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentRecord;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentUploadCommand;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.patient.service.PatientService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ClinicalDocumentController {
    private static final Duration DOWNLOAD_TTL = Duration.ofMinutes(10);

    private final ClinicalDocumentService documentService;
    private final PatientService patientService;
    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public ClinicalDocumentController(
            ClinicalDocumentService documentService,
            PatientService patientService,
            ConsultationService consultationService,
            PrescriptionService prescriptionService,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService
    ) {
        this.documentService = documentService;
        this.patientService = patientService;
        this.consultationService = consultationService;
        this.prescriptionService = prescriptionService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping("/patients/{patientId}/documents")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public List<ClinicalDocumentResponse> listPatientDocuments(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        return documentService.listByPatient(tenantId, patientId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/patients/{patientId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('clinic.document.upload')")
    public ClinicalDocumentResponse uploadPatientDocument(
            @PathVariable UUID patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam(required = false) UUID consultationId,
            @RequestParam(required = false) UUID appointmentId,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String referredDoctor,
            @RequestParam(required = false) String referredHospital,
            @RequestParam(required = false) String referralNotes
    ) throws Exception {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        ClinicalDocumentRecord record = documentService.upload(new ClinicalDocumentUploadCommand(
                tenantId,
                patientId,
                consultationId,
                appointmentId,
                actorAppUserId,
                parseType(documentType),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                notes,
                referredDoctor,
                referredHospital,
                referralNotes
        ));
        return toResponse(record);
    }

    @GetMapping("/patient-documents/{documentId}")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public ClinicalDocumentResponse getDocument(@PathVariable UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId());
        return toResponse(record);
    }

    @GetMapping("/patient-documents/{documentId}/download-url")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.document.read') or @permissionChecker.hasPermission('patient.read')")
    public DocumentDownloadUrlResponse downloadUrl(@PathVariable UUID documentId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        ClinicalDocumentRecord record = documentService.get(tenantId, documentId);
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId());
        return new DocumentDownloadUrlResponse(documentService.downloadUrl(tenantId, documentId, DOWNLOAD_TTL), String.valueOf(DOWNLOAD_TTL.toSeconds()));
    }

    @GetMapping("/patients/{patientId}/timeline")
    @PreAuthorize("@permissionChecker.hasPermission('patient.read')")
    public List<PatientTimelineItemResponse> patientTimeline(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        requirePatientExistsAndVisible(tenantId, patientId);
        List<PatientTimelineItemResponse> items = new ArrayList<>();
        documentService.listByPatient(tenantId, patientId).forEach(doc -> items.add(new PatientTimelineItemResponse(
                doc.id().toString(), "DOCUMENT", documentTypeLabel(doc.documentType()), doc.originalFilename(), doc.createdAt().toString(), null,
                doc.documentType().name(), doc.id().toString(), doc.consultationId() == null ? null : doc.consultationId().toString(), null
        )));
        consultationService.listByPatient(tenantId, patientId).forEach(row -> items.add(new PatientTimelineItemResponse(
                row.id().toString(), "CONSULTATION", row.diagnosis() == null || row.diagnosis().isBlank() ? "Consultation" : row.diagnosis(), row.status().name(), row.createdAt().toString(), row.status().name(), null, null, row.id().toString(), null
        )));
        prescriptionService.listByPatient(tenantId, patientId).forEach(row -> items.add(new PatientTimelineItemResponse(
                row.id().toString(), "PRESCRIPTION", row.prescriptionNumber(), row.status().name(), row.createdAt().toString(), row.status().name(), "PRESCRIPTION", null, row.consultationId().toString(), row.id().toString()
        )));
        return items.stream().sorted(Comparator.comparing(PatientTimelineItemResponse::occurredAt).reversed()).limit(100).toList();
    }

    private void requirePatientExistsAndVisible(UUID tenantId, UUID patientId) {
        patientService.findById(tenantId, patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, patientId);
    }

    private ClinicalDocumentType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document type is required");
        }
        try {
            return ClinicalDocumentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported clinical document type");
        }
    }

    private String documentTypeLabel(ClinicalDocumentType type) {
        return switch (type) {
            case LAB_REPORT -> "Lab Report";
            case PRESCRIPTION -> "Prescription";
            case X_RAY -> "X-Ray";
            case MRI_CT -> "MRI/CT";
            case REFERRAL -> "Referral";
            case DISCHARGE_SUMMARY -> "Discharge Summary";
            case INSURANCE -> "Insurance";
            case VACCINATION -> "Vaccination";
            case OTHER -> "Other";
        };
    }

    private ClinicalDocumentResponse toResponse(ClinicalDocumentRecord record) {
        return new ClinicalDocumentResponse(
                record.id().toString(), record.patientId().toString(), record.consultationId() == null ? null : record.consultationId().toString(),
                record.appointmentId() == null ? null : record.appointmentId().toString(), record.uploadedByAppUserId().toString(), record.documentType().name(),
                record.originalFilename(), record.mediaType(), record.sizeBytes(), record.checksumSha256(), record.notes(), record.referredDoctor(),
                record.referredHospital(), record.referralNotes(), record.aiExtractionStatus(), record.ocrStatus(), record.createdAt().toString(), record.updatedAt().toString()
        );
    }
}
