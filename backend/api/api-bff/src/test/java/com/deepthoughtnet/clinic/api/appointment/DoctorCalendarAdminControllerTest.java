package com.deepthoughtnet.clinic.api.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.security.DoctorAssignmentSecurityService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorCalendarReconcileResult;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DoctorCalendarAdminControllerTest {
    private AppointmentService appointmentService;
    private DoctorAssignmentSecurityService doctorAssignmentSecurityService;
    private DoctorCalendarAdminController controller;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        appointmentService = mock(AppointmentService.class);
        doctorAssignmentSecurityService = mock(DoctorAssignmentSecurityService.class);
        controller = new DoctorCalendarAdminController(appointmentService, doctorAssignmentSecurityService);
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(
                TenantId.of(tenantId),
                actorId,
                "clinic-admin-sub",
                Set.of(),
                "CLINIC_ADMIN",
                "test-correlation"
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void reconcileWorksForClinicAdmin() {
        OffsetDateTime now = OffsetDateTime.now();
        when(doctorAssignmentSecurityService.isClinicAdmin()).thenReturn(true);
        when(appointmentService.reconcileDoctorCalendars(tenantId, actorId, "admin.reconcile"))
                .thenReturn(new DoctorCalendarReconcileResult(tenantId, 2, 3, now));

        DoctorCalendarAdminController.DoctorCalendarReconcileResponse response = controller.reconcile();

        assertThat(response.tenantId()).isEqualTo(tenantId.toString());
        assertThat(response.createdCount()).isEqualTo(2);
        assertThat(response.skippedCount()).isEqualTo(3);
        assertThat(response.timestamp()).isEqualTo(now);
        verify(appointmentService).reconcileDoctorCalendars(tenantId, actorId, "admin.reconcile");
    }

    @Test
    void reconcileRejectsNonClinicAdmin() {
        when(doctorAssignmentSecurityService.isClinicAdmin()).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> controller.reconcile());

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
