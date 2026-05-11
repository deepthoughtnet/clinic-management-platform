package com.deepthoughtnet.clinic.api.appointment;

import com.deepthoughtnet.clinic.api.appointment.dto.AppointmentRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.WaitlistRequest;
import com.deepthoughtnet.clinic.api.appointment.dto.WaitlistResponse;
import com.deepthoughtnet.clinic.api.appointment.dto.WaitlistStatusRequest;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentUpsertCommand;
import com.deepthoughtnet.clinic.appointment.service.model.WaitlistCreateCommand;
import com.deepthoughtnet.clinic.appointment.service.model.WaitlistRecord;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/appointments/waitlist")
public class AppointmentWaitlistController {
    private final AppointmentService appointmentService;

    public AppointmentWaitlistController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public WaitlistResponse create(@Valid @RequestBody WaitlistRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(appointmentService.createWaitlist(tenantId, new WaitlistCreateCommand(
                request.patientId(),
                request.doctorUserId(),
                request.preferredDate(),
                request.preferredStartTime(),
                request.preferredEndTime(),
                request.reason(),
                request.notes()
        ), actorAppUserId));
    }

    @GetMapping
    @PreAuthorize("@permissionChecker.hasPermission('appointment.read') or @permissionChecker.hasPermission('appointment.manage')")
    public List<WaitlistResponse> list(
            @RequestParam(required = false) UUID doctorUserId,
            @RequestParam(required = false) LocalDate preferredDate,
            @RequestParam(required = false) com.deepthoughtnet.clinic.appointment.service.model.WaitlistStatus status
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        return appointmentService.listWaitlist(tenantId, doctorUserId, preferredDate, status).stream().map(this::toResponse).toList();
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public WaitlistResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody WaitlistStatusRequest request) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        return toResponse(appointmentService.updateWaitlistStatus(tenantId, id, request.status(), actorAppUserId));
    }

    @PostMapping("/{id}/book")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissionChecker.hasPermission('appointment.manage')")
    public com.deepthoughtnet.clinic.api.appointment.dto.AppointmentResponse convertToAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody AppointmentRequest request
    ) {
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        boolean allowOverbooking = true;
        var record = appointmentService.convertWaitlistToAppointment(tenantId, id, new AppointmentUpsertCommand(
                request.patientId(),
                request.doctorUserId(),
                request.appointmentDate(),
                request.appointmentTime(),
                request.reason(),
                request.type(),
                request.status(),
                request.priority()
        ), actorAppUserId, allowOverbooking);
        return new com.deepthoughtnet.clinic.api.appointment.dto.AppointmentResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.patientMobile(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                null,
                record.appointmentDate(),
                record.appointmentTime(),
                record.tokenNumber(),
                record.reason(),
                record.type(),
                record.priority(),
                record.status(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private WaitlistResponse toResponse(WaitlistRecord record) {
        return new WaitlistResponse(
                record.id() == null ? null : record.id().toString(),
                record.tenantId() == null ? null : record.tenantId().toString(),
                record.patientId() == null ? null : record.patientId().toString(),
                record.patientNumber(),
                record.patientName(),
                record.doctorUserId() == null ? null : record.doctorUserId().toString(),
                record.doctorName(),
                record.preferredDate(),
                record.preferredStartTime(),
                record.preferredEndTime(),
                record.reason(),
                record.notes(),
                record.status(),
                record.bookedAppointmentId() == null ? null : record.bookedAppointmentId().toString(),
                record.createdAt(),
                record.updatedAt()
        );
    }
}
