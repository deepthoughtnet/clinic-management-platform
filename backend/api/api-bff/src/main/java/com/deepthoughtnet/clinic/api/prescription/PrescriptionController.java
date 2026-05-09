package com.deepthoughtnet.clinic.api.prescription;

import com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionRequest;
import com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionMedicineRequest;
import com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionTestRequest;
import com.deepthoughtnet.clinic.api.prescription.dto.PrescriptionResponse;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineCommand;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestCommand;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionUpsertCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@Validated
@RequestMapping("/api/prescriptions")
public class PrescriptionController {
    private final PrescriptionService prescriptionService;
    private final NotificationActionService notificationActionService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    private final PrescriptionTemplateService prescriptionTemplateService;

    public PrescriptionController(
            PrescriptionService prescriptionService,
            NotificationActionService notificationActionService,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService,
            PrescriptionTemplateService prescriptionTemplateService
    ) {
        this.prescriptionService = prescriptionService;
        this.notificationActionService = notificationActionService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
        this.prescriptionTemplateService = prescriptionTemplateService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('prescription.read')")
    public List<PrescriptionResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        if (doctorAssignmentSecurityService.isDoctor()) {
            return prescriptionService.listByDoctor(tenantId, doctorAssignmentSecurityService.currentDoctorUserId()).stream().map(this::toResponse).toList();
        }
        return prescriptionService.list(tenantId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('prescription.create')")
    public PrescriptionResponse create(@Valid @RequestBody PrescriptionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationCommandAccess(tenantId, request.patientId(), request.doctorUserId(), request.appointmentId());
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(prescriptionService.createDraft(tenantId, toCommand(request), actorAppUserId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.read')")
    public PrescriptionResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        prescriptionService.findById(tenantId, id).ifPresent(record -> doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId()));
        return toResponse(prescriptionService.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found")));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.read')")
    public List<PrescriptionResponse> history(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        PrescriptionRecord current = prescriptionService.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found"));
        doctorAssignmentSecurityService.requirePatientAccess(tenantId, current.patientId());
        return prescriptionService.history(tenantId, id).stream().map(this::toResponse).toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.create')")
    public PrescriptionResponse update(@PathVariable UUID id, @Valid @RequestBody PrescriptionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationCommandAccess(tenantId, request.patientId(), request.doctorUserId(), request.appointmentId());
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(prescriptionService.updateDraft(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/{id}/finalize")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.finalize')")
    public PrescriptionResponse finalizePrescription(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        prescriptionService.findById(tenantId, id).ifPresent(record -> doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId()));
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(prescriptionService.finalizePrescription(tenantId, id, actorAppUserId));
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.create')")
    public PrescriptionResponse previewPrescription(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        prescriptionService.findById(tenantId, id).ifPresent(record -> doctorAssignmentSecurityService.requirePatientAccess(tenantId, record.patientId()));
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(prescriptionService.previewPrescription(tenantId, id, actorAppUserId));
    }

    @PostMapping("/{id}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('prescription.create')")
    public PrescriptionResponse createCorrection(@PathVariable UUID id, @Valid @RequestBody CorrectionRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationCommandAccess(tenantId, request.prescription().patientId(), request.prescription().doctorUserId(), request.prescription().appointmentId());
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(prescriptionService.createCorrectionVersion(
                tenantId,
                id,
                toCommand(request.prescription()),
                actorAppUserId,
                request.flowType() == null ? "SAME_DAY_CORRECTION" : request.flowType(),
                request.correctionReason()
        ));
    }

    @PostMapping("/{id}/print")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.print')")
    public PrescriptionResponse print(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(prescriptionService.markPrinted(tenantId, id, actorAppUserId));
    }

    @PostMapping("/{id}/mark-sent")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.send')")
    public PrescriptionResponse markSent(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return sendInternal(tenantId, id, "email", actorAppUserId);
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.send')")
    public PrescriptionResponse send(@PathVariable UUID id, @RequestBody(required = false) SendNotificationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        String channel = request == null || request.channel() == null ? "email" : request.channel();
        return sendInternal(tenantId, id, channel, actorAppUserId);
    }

    private PrescriptionResponse sendInternal(UUID tenantId, UUID id, String channel, UUID actorAppUserId) {
        notificationActionService.sendPrescription(tenantId, id, channel, actorAppUserId);
        return toResponse(prescriptionService.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found")));
    }

    @GetMapping("/consultations/{consultationId}")
    @PreAuthorize("@permissionChecker.hasPermission('prescription.read')")
    public PrescriptionResponse getByConsultation(@PathVariable UUID consultationId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        doctorAssignmentSecurityService.requireConsultationAccess(tenantId, consultationId);
        return toResponse(prescriptionService.findByConsultationId(tenantId, consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Prescription not found")));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("@permissionChecker.hasPermission('prescription.print')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        PrescriptionPdf pdf = prescriptionService.generatePdf(tenantId, id, actorAppUserId, prescriptionTemplateService.toPdfConfig(prescriptionTemplateService.getActive(tenantId)));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(pdf.filename()).build().toString())
                .body(pdf.content());
    }

    private PrescriptionUpsertCommand toCommand(PrescriptionRequest request) {
        return new PrescriptionUpsertCommand(
                request.patientId(),
                request.doctorUserId(),
                request.consultationId(),
                request.appointmentId(),
                request.diagnosisSnapshot(),
                request.advice(),
                request.followUpDate(),
                request.medicines() == null ? List.of() : request.medicines().stream().map(PrescriptionMedicineRequest::toCommand).toList(),
                request.recommendedTests() == null ? List.of() : request.recommendedTests().stream().map(PrescriptionTestRequest::toCommand).toList()
        );
    }

    private PrescriptionResponse toResponse(PrescriptionRecord record) {
        return new PrescriptionResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.consultationId() == null ? null : record.consultationId().toString(),
                record.appointmentId() == null ? null : record.appointmentId().toString(),
                record.prescriptionNumber(),
                record.versionNumber(),
                record.parentPrescriptionId() == null ? null : record.parentPrescriptionId().toString(),
                record.correctionReason(),
                record.flowType(),
                record.correctedAt(),
                record.supersededByPrescriptionId() == null ? null : record.supersededByPrescriptionId().toString(),
                record.supersededAt(),
                record.diagnosisSnapshot(),
                record.advice(),
                record.followUpDate(),
                record.status(),
                record.finalizedAt(),
                record.finalizedByDoctorUserId() == null ? null : record.finalizedByDoctorUserId().toString(),
                record.printedAt(),
                record.sentAt(),
                record.createdAt(),
                record.updatedAt(),
                record.medicines(),
                record.recommendedTests()
        );
    }

    public record SendNotificationRequest(String channel) {
    }

    public record CorrectionRequest(@NotBlank @Size(max = 512) String correctionReason, String flowType, @Valid PrescriptionRequest prescription) {
    }
}
