package com.deepthoughtnet.clinic.api.appointment;

import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorCalendarReconcileResult;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/admin/doctor-calendars")
public class DoctorCalendarAdminController {
    private final AppointmentService appointmentService;
    private final DoctorAssignmentSecurityService doctorAssignmentSecurityService;

    public DoctorCalendarAdminController(
            AppointmentService appointmentService,
            DoctorAssignmentSecurityService doctorAssignmentSecurityService
    ) {
        this.appointmentService = appointmentService;
        this.doctorAssignmentSecurityService = doctorAssignmentSecurityService;
    }

    @PostMapping("/reconcile")
    @PreAuthorize("@permissionChecker.hasAnyPermission('tenant.users.manage','user.manage')")
    public DoctorCalendarReconcileResponse reconcile() {
        if (!doctorAssignmentSecurityService.isClinicAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only clinic administrators can reconcile doctor calendars");
        }
        UUID tenantId = RequestContextHolder.requireTenantId();
        UUID actorAppUserId = RequestContextHolder.require().appUserId();
        DoctorCalendarReconcileResult result = appointmentService.reconcileDoctorCalendars(
                tenantId,
                actorAppUserId,
                "admin.reconcile"
        );
        return new DoctorCalendarReconcileResponse(
                result.tenantId().toString(),
                result.createdCount(),
                result.skippedCount(),
                result.timestamp()
        );
    }

    public record DoctorCalendarReconcileResponse(
            String tenantId,
            int createdCount,
            int skippedCount,
            OffsetDateTime timestamp
    ) {}
}
