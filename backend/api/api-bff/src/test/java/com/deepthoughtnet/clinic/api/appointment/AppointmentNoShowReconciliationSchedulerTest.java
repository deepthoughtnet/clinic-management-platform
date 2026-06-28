package com.deepthoughtnet.clinic.api.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentPriority;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentType;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.identity.service.model.TenantModulesRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.NotificationRecipientResolver;
import com.deepthoughtnet.clinic.notification.service.model.NotificationHistoryRecord;
import com.deepthoughtnet.clinic.notification.service.model.NotificationQueueResult;
import com.deepthoughtnet.clinic.platform.spring.lock.DistributedLockService;
import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentNoShowReconciliationSchedulerTest {

    @Test
    void marksDueAppointmentsNoShowAndNotifiesStaff() {
        PlatformTenantManagementService tenantManagementService = mock(PlatformTenantManagementService.class);
        AppointmentService appointmentService = mock(AppointmentService.class);
        NotificationActionService notificationActionService = mock(NotificationActionService.class);
        NotificationHistoryService notificationHistoryService = mock(NotificationHistoryService.class);
        NotificationRecipientResolver notificationRecipientResolver = mock(NotificationRecipientResolver.class);
        com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver clinicTimeZoneResolver = mock(com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver.class);
        DistributedLockService lockService = mock(DistributedLockService.class);
        SchedulerLockMonitor schedulerLockMonitor = mock(SchedulerLockMonitor.class);

        UUID tenantId = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);
        AppointmentRecord appointment = new AppointmentRecord(
                UUID.randomUUID(),
                tenantId,
                UUID.randomUUID(),
                "PAT-001",
                "Riya Sharma",
                "9999999999",
                UUID.randomUUID(),
                "Dr Mehta",
                null,
                yesterday,
                LocalTime.of(10, 0),
                1,
                "Checkup",
                AppointmentType.SCHEDULED,
                AppointmentPriority.NORMAL,
                AppointmentStatus.BOOKED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        when(tenantManagementService.list()).thenReturn(List.of(new PlatformTenantRecord(
                tenantId,
                "tenant-code",
                "Tenant",
                "PLAN",
                "ACTIVE",
                true,
                new TenantModulesRecord(true, true, true, true, true, true, true, true, true, true),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        )));
        when(clinicTimeZoneResolver.resolve(tenantId)).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(appointmentService.search(eq(tenantId), any(AppointmentSearchCriteria.class))).thenAnswer(invocation -> {
            AppointmentSearchCriteria criteria = invocation.getArgument(1);
            return yesterday.equals(criteria.appointmentDate()) ? List.of(appointment) : List.of();
        });
        when(appointmentService.updateStatus(eq(tenantId), eq(appointment.id()), any(), isNull())).thenReturn(appointment);
        when(notificationRecipientResolver.resolveEmailsByRoles(eq(tenantId), anyList())).thenReturn(List.of("admin@example.com", "reception@example.com"));
        when(lockService.executeWithLock(anyString(), any(Duration.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<Void> callback = invocation.getArgument(2);
            callback.get();
            return true;
        });
        when(notificationHistoryService.queueDetailed(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new NotificationQueueResult(
                        new NotificationHistoryRecord(
                                UUID.randomUUID(),
                                tenantId,
                                null,
                                "APPOINTMENT_NO_SHOW_STAFF_ALERT",
                                "email",
                                "recipient@example.com",
                                "subject",
                                "message",
                                "QUEUED",
                                null,
                                "APPOINTMENT",
                                appointment.id(),
                                null,
                                null,
                                1,
                                null,
                                null,
                                OffsetDateTime.now(),
                                OffsetDateTime.now()
                        ),
                        true
                ));

        AppointmentNoShowReconciliationScheduler scheduler = new AppointmentNoShowReconciliationScheduler(
                tenantManagementService,
                appointmentService,
                notificationActionService,
                notificationHistoryService,
                notificationRecipientResolver,
                clinicTimeZoneResolver,
                lockService,
                schedulerLockMonitor,
                Duration.ofSeconds(1),
                7
        );

        scheduler.reconcileNoShows();

        verify(appointmentService).updateStatus(eq(tenantId), eq(appointment.id()), any(), isNull());
        verify(notificationActionService).sendAppointmentNoShow(eq(tenantId), eq(appointment.id()), isNull());
        verify(notificationHistoryService, times(2)).queueDetailed(eq(tenantId), isNull(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(appointment.id()), isNull());
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.BOOKED);
    }
}
