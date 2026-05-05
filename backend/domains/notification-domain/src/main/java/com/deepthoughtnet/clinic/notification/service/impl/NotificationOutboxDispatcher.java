package com.deepthoughtnet.clinic.notification.service.impl;

import com.deepthoughtnet.clinic.notification.service.NotificationDispatcher;
import com.deepthoughtnet.clinic.notification.service.NotificationDispatcher.NotificationDispatchSettings;
import com.deepthoughtnet.clinic.notification.service.NotificationProperties;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "clinic.notifications.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxDispatcher.class);

    private final NotificationDispatcher notificationDispatcher;
    private final NotificationProperties properties;

    public NotificationOutboxDispatcher(
            NotificationDispatcher notificationDispatcher,
            NotificationProperties properties
    ) {
        this.notificationDispatcher = notificationDispatcher;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${clinic.notifications.dispatcher.pollingIntervalMs:30000}",
            initialDelayString = "${clinic.notifications.dispatcher.initialDelayMs:10000}"
    )
    public void dispatchDueNotifications() {
        if (!properties.getDispatcher().isEnabled()) {
            return;
        }
        NotificationDispatchSettings settings = new NotificationDispatchSettings(
                properties.getChannel(),
                properties.getDispatcher().normalizedBatchSize(),
                properties.getDispatcher().normalizedMaxAttempts(),
                properties.getDispatcher().normalizedRetryBackoff()
        );
        List<UUID> eventIds = notificationDispatcher.findDueNotificationIds(settings);
        if (eventIds.isEmpty()) {
            return;
        }

        log.info("Notification dispatcher picked {} outbox event(s)", eventIds.size());
        for (UUID eventId : eventIds) {
            notificationDispatcher.dispatchOne(eventId, settings);
        }
    }
}
