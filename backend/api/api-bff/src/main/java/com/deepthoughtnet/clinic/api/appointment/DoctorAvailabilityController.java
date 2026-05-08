package com.deepthoughtnet.clinic.api.appointment;

import com.deepthoughtnet.clinic.api.appointment.dto.DoctorAvailabilityRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.DoctorAvailabilityResponse;
import com.deepthoughtnet.clinic.api.appointment.dto.DoctorAvailabilitySlotResponse;
import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilitySlotRecord;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityUpsertCommand;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
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
@RequestMapping("/api/doctors")
public class DoctorAvailabilityController {
    private final AppointmentService appointmentService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public DoctorAvailabilityController(AppointmentService appointmentService, DoctorAssignmentSecurityService doctorAssignmentSecurityService) {
        this.appointmentService = appointmentService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @GetMapping("/availability")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public List<DoctorAvailabilityResponse> list() {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return appointmentService.listAvailabilities(tenantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{doctorUserId}/slots")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')")
    public List<DoctorAvailabilitySlotResponse> slots(@PathVariable UUID doctorUserId, @org.springframework.web.bind.annotation.RequestParam("date") LocalDate date) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID effectiveDoctorUserId = doctorAssignmentSecurityService.effectiveDoctorUserId(doctorUserId);
        return appointmentService.listSlots(tenantId, effectiveDoctorUserId, date).stream().map(this::toSlotResponse).toList();
    }

    @GetMapping("/{doctorUserId}/queue/today")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public List<com.deepthoughtnet.clinic.api.appointment.dto.AppointmentResponse> queueToday(@PathVariable UUID doctorUserId) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID effectiveDoctorUserId = doctorAssignmentSecurityService.effectiveDoctorUserId(doctorUserId);
        return appointmentService.listQueueToday(tenantId, effectiveDoctorUserId).stream()
                .map(record -> new com.deepthoughtnet.clinic.api.appointment.dto.AppointmentResponse(
                        record.id() == null ? null : record.id().toString(),
                        record.tenantId() == null ? null : record.tenantId().toString(),
                        record.patientId() == null ? null : record.patientId().toString(),
                        record.patientNumber(),
                        record.patientName(),
                        record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                        record.doctorName(),
                        record.appointmentDate(),
                        record.appointmentTime(),
                        record.tokenNumber(),
                        record.reason(),
                        record.type(),
                        record.priority(),
                        record.status(),
                        record.createdAt(),
                        record.updatedAt()
                ))
                .toList();
    }

    @PostMapping("/{doctorUserId}/availability")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public DoctorAvailabilityResponse create(@PathVariable UUID doctorUserId, @RequestBody DoctorAvailabilityRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(appointmentService.createAvailability(tenantId, doctorUserId, toCommand(request), actorAppUserId));
    }

    @PutMapping("/availability/{id}")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public DoctorAvailabilityResponse update(@PathVariable UUID id, @RequestBody DoctorAvailabilityRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(appointmentService.updateAvailability(tenantId, id, toCommand(request), actorAppUserId));
    }

    @PatchMapping("/availability/{id}/deactivate")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public DoctorAvailabilityResponse deactivate(@PathVariable UUID id) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(appointmentService.deactivateAvailability(tenantId, id, actorAppUserId));
    }

    private DoctorAvailabilityUpsertCommand toCommand(DoctorAvailabilityRequest request) {
        return new DoctorAvailabilityUpsertCommand(
                request.dayOfWeek(),
                request.startTime(),
                request.endTime(),
                request.breakStartTime(),
                request.breakEndTime(),
                request.consultationDurationMinutes(),
                request.maxPatientsPerSlot(),
                request.active()
        );
    }

    private DoctorAvailabilityResponse toResponse(DoctorAvailabilityRecord record) {
        return new DoctorAvailabilityResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.dayOfWeek(),
                record.startTime(),
                record.endTime(),
                record.breakStartTime(),
                record.breakEndTime(),
                record.consultationDurationMinutes(),
                record.maxPatientsPerSlot(),
                record.active(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private DoctorAvailabilitySlotResponse toSlotResponse(DoctorAvailabilitySlotRecord record) {
        return new DoctorAvailabilitySlotResponse(
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.appointmentDate(),
                record.slotTime(),
                record.slotEndTime(),
                record.status(),
                record.bookedCount(),
                record.maxPatientsPerSlot(),
                record.selectable(),
                record.appointmentId() == null ? null : record.appointmentId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.tokenNumber(),
                record.appointmentStatus(),
                record.reason()
        );
    }
}
