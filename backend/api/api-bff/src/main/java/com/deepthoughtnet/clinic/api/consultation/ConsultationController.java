package com.deepthoughtnet.clinic.api.consultation;

import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationRequest;
import com.deepthoughtnet.clinic.api.consultation.dto.ConsultationResponse;
import com.deepthoughtnet.clinic.consultation.service.ConsultationService;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationRecord;
import com.deepthoughtnet.clinic.consultation.service.model.ConsultationUpsertCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {
    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('consultation.read')")
    public List<ConsultationResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return consultationService.list(tenantId).stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('consultation.create')")
    public ConsultationResponse create(@RequestBody ConsultationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(consultationService.createDraft(tenantId, toCommand(request), actorAppUserId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.read')")
    public ConsultationResponse get(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return toResponse(consultationService.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Consultation not found")));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.update')")
    public ConsultationResponse update(@PathVariable UUID id, @RequestBody ConsultationRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(consultationService.update(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.update')")
    public ConsultationResponse complete(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(consultationService.complete(tenantId, id, actorAppUserId));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("@permissionChecker.hasPermission('consultation.update')")
    public ConsultationResponse cancel(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(consultationService.cancel(tenantId, id, actorAppUserId));
    }

    private ConsultationUpsertCommand toCommand(ConsultationRequest request) {
        return new ConsultationUpsertCommand(
                request.patientId(),
                request.doctorUserId(),
                request.appointmentId(),
                request.chiefComplaints(),
                request.symptoms(),
                request.diagnosis(),
                request.clinicalNotes(),
                request.advice(),
                request.followUpDate(),
                request.bloodPressureSystolic(),
                request.bloodPressureDiastolic(),
                request.pulseRate(),
                request.temperature(),
                request.temperatureUnit(),
                request.weightKg(),
                request.heightCm(),
                request.spo2()
        );
    }

    private ConsultationResponse toResponse(ConsultationRecord record) {
        return new ConsultationResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.appointmentId() == null ? null : record.appointmentId().toString(),
                record.chiefComplaints(),
                record.symptoms(),
                record.diagnosis(),
                record.clinicalNotes(),
                record.advice(),
                record.followUpDate(),
                record.status(),
                record.bloodPressureSystolic(),
                record.bloodPressureDiastolic(),
                record.pulseRate(),
                record.temperature(),
                record.temperatureUnit(),
                record.weightKg(),
                record.heightCm(),
                record.spo2(),
                record.completedAt(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
