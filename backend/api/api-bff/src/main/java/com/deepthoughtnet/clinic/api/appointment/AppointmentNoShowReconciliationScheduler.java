package com.deepthoughtnet.clinic.api.appointment;

import com.deepthoughtnet.clinic.api.notifications.NotificationActionService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.appointment.service.AppointmentService;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentRecord;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentSearchCriteria;
import com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatus;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import com.deepthoughtnet.clinic.identity.service.model.PlatformTenantRecord;
import com.deepthoughtnet.clinic.notification.service.NotificationHistoryService;
import com.deepthoughtnet.clinic.notification.service.NotificationRecipientResolver;
import com.deepthoughtnet.clinic.platform.security.Roles;
import com.deepthoughtnet.clinic.platform.spring.lock.DistributedLockService;
import com.deepthoughtnet.clinic.api.ops.SchedulerLockMonitor;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AppointmentNoShowReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(AppointmentNoShowReconciliationScheduler.class);
    private static final String LOCK_KEY = "scheduler:appointment-no-show-reconciliation";

    private final PlatformTenantManagementService tenantManagementService;
    private final AppointmentService appointmentService;
    private final NotificationActionService notificationActionService;
    private final NotificationHistoryService notificationHistoryService;
    private final NotificationRecipientResolver notificationRecipientResolver;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final DistributedLockService lockService;
    private final SchedulerLockMonitor schedulerLockMonitor;
    private final Duration lockWaitTimeout;
    private final int daysBack;

    public AppointmentNoShowReconciliationScheduler(
            PlatformTenantManagementService tenantManagementService,
            AppointmentService appointmentService,
            NotificationActionService notificationActionService,
            NotificationHistoryService notificationHistoryService,
            NotificationRecipientResolver notificationRecipientResolver,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            DistributedLockService lockService,
            SchedulerLockMonitor schedulerLockMonitor,
            @Value("${platform.locks.scheduler-wait-timeout:PT2S}") Duration lockWaitTimeout,
            @Value("${clinic.appointments.no-show.days-back:7}") int daysBack
    ) {
        this.tenantManagementService = tenantManagementService;
        this.appointmentService = appointmentService;
        this.notificationActionService = notificationActionService;
        this.notificationHistoryService = notificationHistoryService;
        this.notificationRecipientResolver = notificationRecipientResolver;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.lockService = lockService;
        this.schedulerLockMonitor = schedulerLockMonitor;
        this.lockWaitTimeout = lockWaitTimeout;
        this.daysBack = Math.max(1, daysBack);
    }

    @Scheduled(fixedDelayString = "${clinic.appointments.no-show.fixed-delay:PT15M}")
    public void reconcileNoShows() {
        boolean ran = lockService.executeWithLock(LOCK_KEY, lockWaitTimeout, () -> {
            schedulerLockMonitor.markAcquired("appointment-no-show-reconciler");
            int marked = 0;
            for (PlatformTenantRecord tenant : tenantManagementService.list()) {
                marked += reconcileTenant(tenant.id(), clinicTimeZoneResolver.resolve(tenant.id()));
            }
            log.info("APPOINTMENT_NO_SHOW_RECONCILIATION_TRACE marked={} daysBack={}", marked, daysBack);
            return null;
        });
        if (!ran) {
            schedulerLockMonitor.markSkipped("appointment-no-show-reconciler");
        }
    }

    private int reconcileTenant(UUID tenantId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        int marked = 0;
        for (int offset = 0; offset < daysBack; offset++) {
            LocalDate date = today.minusDays(offset);
            List<AppointmentRecord> appointments = appointmentService.search(
                    tenantId,
                    new AppointmentSearchCriteria(null, null, date, null, null)
            );
            for (AppointmentRecord appointment : appointments) {
                if (!shouldMarkNoShow(appointment, zone)) {
                    continue;
                }
                appointmentService.updateStatus(
                        tenantId,
                        appointment.id(),
                        new com.deepthoughtnet.clinic.appointment.service.model.AppointmentStatusUpdateCommand(
                                AppointmentStatus.NO_SHOW,
                                "Marked no-show after reconciliation window",
                                null,
                                null,
                                null
                        ),
                        null
                );
                if (notificationActionService != null) {
                    notificationActionService.sendAppointmentNoShow(tenantId, appointment.id(), null);
                }
                queueStaffAlert(tenantId, appointment);
                marked++;
            }
        }
        if (marked > 0) {
            log.info(
                    "APPOINTMENT_NO_SHOW_RECONCILIATION_TRACE tenantId={} zone={} marked={}",
                    tenantId,
                    zone,
                    marked
            );
        }
        return marked;
    }

    private void queueStaffAlert(UUID tenantId, AppointmentRecord appointment) {
        List<String> recipients = notificationRecipientResolver.resolveEmailsByRoles(
                tenantId,
                List.of(Roles.CLINIC_ADMIN, Roles.RECEPTIONIST)
        );
        if (recipients.isEmpty()) {
            return;
        }
        String subject = "Appointment marked no-show";
        String message = buildStaffMessage(appointment);
        for (String recipient : recipients) {
            notificationHistoryService.queueDetailed(
                    tenantId,
                    null,
                    "APPOINTMENT_NO_SHOW_STAFF_ALERT",
                    "email",
                    recipient,
                    subject,
                    message,
                    "APPOINTMENT",
                    appointment.id(),
                    null
            );
        }
    }

    private String buildStaffMessage(AppointmentRecord appointment) {
        String doctor = appointment.doctorName() == null ? "the doctor" : appointment.doctorName();
        String date = appointment.appointmentDate() == null ? "the appointment date" : appointment.appointmentDate().toString();
        String time = appointment.appointmentTime() == null ? "the appointment time" : appointment.appointmentTime().toString();
        return "Appointment for " + doctor + " on " + date + " at " + time + " was marked as no-show.";
    }

    private boolean shouldMarkNoShow(AppointmentRecord appointment, ZoneId zone) {
        if (appointment == null || appointment.status() == null) {
            return false;
        }
        if (appointment.status() == AppointmentStatus.CANCELLED
                || appointment.status() == AppointmentStatus.COMPLETED
                || appointment.status() == AppointmentStatus.NO_SHOW) {
            return false;
        }
        return AppointmentTimingRules.isNoShowEligible(appointment, zone);
    }

}
