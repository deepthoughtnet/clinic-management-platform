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
        int queued = 0;
        LocalDate today = LocalDate.now();
        for (var tenant : tenantManagementService.list()) {
            queued += notificationActionService.queueAppointmentReminders(tenant.id(), null, null);
            queued += notificationActionService.queueFollowUpReminders(tenant.id(), today, null);
            queued += notificationActionService.queueVaccinationReminders(tenant.id(), null);
        }
        if (queued > 0) {
            log.info("Queued {} reminder notification(s)", queued);
        }
    }
}
