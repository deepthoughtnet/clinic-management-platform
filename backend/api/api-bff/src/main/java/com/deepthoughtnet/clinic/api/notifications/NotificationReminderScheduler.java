package com.deepthoughtnet.clinic.api.notifications;

import com.deepthoughtnet.clinic.identity.service.PlatformTenantManagementService;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
        if (!properties.enabled()) {
            return;
        }
        NotificationActionService.ReminderQueueSummary totals = NotificationActionService.ReminderQueueSummary.empty();
        for (var tenant : tenantManagementService.list()) {
            totals = totals.add(notificationActionService.queueFollowUpReminders(tenant.id(), LocalDate.now(), null));
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
