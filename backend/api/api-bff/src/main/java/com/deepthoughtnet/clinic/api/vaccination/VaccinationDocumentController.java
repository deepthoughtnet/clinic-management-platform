package com.deepthoughtnet.clinic.api.vaccination;

import com.deepthoughtnet.clinic.api.clinicaldocument.service.ClinicalDocumentService;
import com.deepthoughtnet.clinic.api.vaccination.document.GeneratedVaccinationDocumentResponse;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationCertificateRequest;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationCertificateService;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationDocumentChannelRequest;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationPassportService;
import com.deepthoughtnet.clinic.api.vaccination.document.VaccinationReminderService;
import com.deepthoughtnet.clinic.platform.audit.AuditEntityType;
import com.deepthoughtnet.clinic.platform.audit.AuditEventCommand;
import com.deepthoughtnet.clinic.platform.audit.AuditEventPublisher;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@Validated
@RequestMapping("/api/patients/{patientId}/vaccination-documents")
public class VaccinationDocumentController {
    private final VaccinationPassportService passportService;
    private final VaccinationCertificateService certificateService;
    private final VaccinationReminderService reminderService;
    private final ClinicalDocumentService clinicalDocumentService;
    private final AuditEventPublisher auditEventPublisher;

    public VaccinationDocumentController(
            VaccinationPassportService passportService,
            VaccinationCertificateService certificateService,
            VaccinationReminderService reminderService,
            ClinicalDocumentService clinicalDocumentService,
            AuditEventPublisher auditEventPublisher
    ) {
        this.passportService = passportService;
        this.certificateService = certificateService;
        this.reminderService = reminderService;
        this.clinicalDocumentService = clinicalDocumentService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @PostMapping("/passport")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public GeneratedVaccinationDocumentResponse generatePassport(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return passportService.generatePassport(tenantId, patientId, actorAppUserId);
    }

    @PostMapping("/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public GeneratedVaccinationDocumentResponse generateCertificate(@PathVariable UUID patientId, @Valid @RequestBody VaccinationCertificateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return certificateService.generateCertificate(tenantId, patientId, request, actorAppUserId);
    }

    @GetMapping(value = "/{documentId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable UUID patientId,
            @PathVariable UUID documentId,
            @RequestParam(defaultValue = "DOWNLOAD") String mode
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        byte[] pdf = passportService.downloadPdf(tenantId, documentId);
        String filename = clinicalDocumentService.get(tenantId, documentId).originalFilename();
        String normalizedMode = mode == null ? "DOWNLOAD" : mode.trim().toUpperCase();
        audit(tenantId, documentId, normalizedMode, RequestContextHolder.require().appUserId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename == null ? "vaccination-document.pdf" : filename).build().toString())
                .body(pdf);
    }

    @PostMapping("/{documentId}/send")
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public ResponseEntity<Void> sendDocument(
            @PathVariable UUID patientId,
            @PathVariable UUID documentId,
            @Valid @RequestBody VaccinationDocumentChannelRequest request
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        passportService.sendDocument(tenantId, documentId, request.channel(), actorAppUserId);
        audit(tenantId, documentId, "SENT", actorAppUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reminders/queue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("@permissionChecker.hasPermission('vaccination.manage')")
    public int queueReminders(@PathVariable UUID patientId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return reminderService.queuePatientReminders(tenantId, patientId, actorAppUserId);
    }

    private void audit(UUID tenantId, UUID documentId, String mode, UUID actorAppUserId) {
        String action = switch (mode) {
            case "PRINT" -> "vaccination.document_printed";
            case "VIEW" -> "vaccination.document_viewed";
            default -> "vaccination.document_downloaded";
        };
        auditEventPublisher.record(new AuditEventCommand(
                tenantId,
                AuditEntityType.DOCUMENT,
                documentId,
                action,
                actorAppUserId,
                OffsetDateTime.now(ZoneOffset.UTC),
                action,
                "{\"mode\":\"" + mode + "\"}"
        ));
    }
}
