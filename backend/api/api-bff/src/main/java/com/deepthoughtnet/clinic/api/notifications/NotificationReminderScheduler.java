package com.deepthoughtnet.clinic.api.notifications;

import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class NotificationReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationReminderScheduler.class);

    private final PlatformTenantManagementService tenantManagementService;
    private final NotificationActionService notificationActionService;
    private final NotificationsSchedulerProperties properties;

    public NotificationReminderScheduler(
            PlatformTenantManagementService tenantManagementService,
            NotificationActionService notificationActionService,
            NotificationsSchedulerProperties properties
    ) {
        this.tenantManagementService = tenantManagementService;
        this.notificationActionService = notificationActionService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${clinic.notifications.scheduler.fixedDelay:PT5M}")
    public void run() {
        NotificationActionService.ReminderQueueSummary totals = NotificationActionService.ReminderQueueSummary.empty();
        LocalDate today = LocalDate.now();
        for (var tenant : tenantManagementService.list()) {
            totals = totals.add(notificationActionService.queueAppointmentReminders(tenant.id(), null, null));
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
