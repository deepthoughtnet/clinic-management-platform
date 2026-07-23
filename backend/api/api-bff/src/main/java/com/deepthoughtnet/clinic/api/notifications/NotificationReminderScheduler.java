package com.deepthoughtnet.clinic.api.notifications;

import com.deepthoughtnet.clinic.appointment.service.AppointmentReminderEventService;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NotificationReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationReminderScheduler.class);

    private final PlatformTenantManagementService tenantManagementService;
    private final ClinicTimeZoneResolver clinicTimeZoneResolver;
    private final NotificationActionService notificationActionService;
    private final AppointmentReminderEventService appointmentReminderEventService;
    private final NotificationsSchedulerProperties properties;
    private final AppointmentReminderProperties appointmentReminderProperties;

    public NotificationReminderScheduler(
            PlatformTenantManagementService tenantManagementService,
            ClinicTimeZoneResolver clinicTimeZoneResolver,
            NotificationActionService notificationActionService,
            AppointmentReminderEventService appointmentReminderEventService,
            NotificationsSchedulerProperties properties,
            AppointmentReminderProperties appointmentReminderProperties
    ) {
        this.tenantManagementService = tenantManagementService;
        this.clinicTimeZoneResolver = clinicTimeZoneResolver;
        this.notificationActionService = notificationActionService;
        this.appointmentReminderEventService = appointmentReminderEventService;
        this.properties = properties;
        this.appointmentReminderProperties = appointmentReminderProperties;
    }

    @Scheduled(fixedDelayString = "${clinic.notifications.scheduler.fixedDelay:PT5M}")
    public void run() {
        if (!properties.enabled()) {
            return;
        }
        NotificationActionService.ReminderQueueSummary totals = NotificationActionService.ReminderQueueSummary.empty();
        LocalDate today = LocalDate.now();
        for (var tenant : tenantManagementService.list()) {
            if (appointmentReminderProperties.isEnabled()) {
                Duration reminderOffset = Duration.ofHours(Math.max(1, appointmentReminderProperties.getHoursBefore()));
                Duration gracePeriod = Duration.ofMinutes(Math.max(0, appointmentReminderProperties.getGraceMinutes()));
                ZoneId bookingZone = clinicTimeZoneResolver.resolve(tenant.id());
                appointmentReminderEventService.publishDueReminders(tenant.id(), bookingZone, reminderOffset, gracePeriod, null);
            }
            totals = totals.add(notificationActionService.queueMissedAppointmentReminders(tenant.id(), today, null));
            totals = totals.add(notificationActionService.queueFollowUpReminders(tenant.id(), today, null));
            totals = totals.add(notificationActionService.queueVaccinationReminders(tenant.id(), null));
            totals = totals.add(notificationActionService.queuePaymentReminders(tenant.id(), null));
        }
        if (totals.queuedCount() > 0 || totals.skippedDuplicateCount() > 0 || totals.failedCount() > 0) {
            log.info(
                    "Reminder queue run complete: queuedCount={}, skippedDuplicateCount={}, failedCount={}",
                    totals.queuedCount(),
                    totals.skippedDuplicateCount(),
                    totals.failedCount()
            );
        }
    }
}
