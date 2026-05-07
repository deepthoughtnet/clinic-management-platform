package com.deepthoughtnet.clinic.api.prescriptiontemplate;

import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateSettings;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.dto.PrescriptionTemplateRequest;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.dto.PrescriptionTemplateResponse;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateRecord;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/prescription-template")
public class PrescriptionTemplateController {
    private final PrescriptionTemplateService templateService;
    private final PrescriptionService prescriptionService;

    public PrescriptionTemplateController(PrescriptionTemplateService templateService, PrescriptionService prescriptionService) {
        this.templateService = templateService;
        this.prescriptionService = prescriptionService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('clinic.profile.read')")
    public PrescriptionTemplateResponse getActive() {
        return toResponse(templateService.getActive(RequestContextHolder.requireTenantId()));
    }

    @GetMapping("/history")
    @PreAuthorize("@permissionChecker.hasPermission('clinic.profile.read')")
    public List<PrescriptionTemplateResponse> history() {
        return templateService.history(RequestContextHolder.requireTenantId()).stream().map(this::toResponse).toList();
    }

    @PutMapping
    @PreAuthorize("@permissionChecker.hasPermission('clinic.profile.update')")
    public PrescriptionTemplateResponse save(@RequestBody PrescriptionTemplateRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(templateService.save(tenantId, actorAppUserId, settings(request)));
    }

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('clinic.profile.update')")
    public ResponseEntity<byte[]> preview(@RequestBody PrescriptionTemplateRequest request, @RequestParam(required = false) UUID prescriptionId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        PrescriptionPdf pdf = prescriptionService.generateTemplatePreviewPdf(tenantId, prescriptionId, actorAppUserId, templateService.toPdfConfig(new PrescriptionTemplateRecord(null, tenantId, 0, true, parseUuid(request.clinicLogoDocumentId()), request.headerText(), request.footerText(), request.primaryColor(), request.accentColor(), request.disclaimer(), request.doctorSignatureText(), request.showQrCode() == null || request.showQrCode(), request.watermarkText(), actorAppUserId, null, null)));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    private PrescriptionTemplateSettings settings(PrescriptionTemplateRequest request) {
        return new PrescriptionTemplateSettings(parseUuid(request.clinicLogoDocumentId()), request.headerText(), request.footerText(), request.primaryColor(), request.accentColor(), request.disclaimer(), request.doctorSignatureText(), request.showQrCode() == null || request.showQrCode(), request.watermarkText());
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        return UUID.fromString(value.trim());
    }

    private PrescriptionTemplateResponse toResponse(PrescriptionTemplateRecord r) {
        return new PrescriptionTemplateResponse(r.id() == null ? null : r.id().toString(), r.tenantId().toString(), r.templateVersion(), r.active(), r.clinicLogoDocumentId() == null ? null : r.clinicLogoDocumentId().toString(), r.headerText(), r.footerText(), r.primaryColor(), r.accentColor(), r.disclaimer(), r.doctorSignatureText(), r.showQrCode(), r.watermarkText(), r.changedByAppUserId() == null ? null : r.changedByAppUserId().toString(), r.createdAt() == null ? null : r.createdAt().toString(), r.updatedAt() == null ? null : r.updatedAt().toString());
    }
}
