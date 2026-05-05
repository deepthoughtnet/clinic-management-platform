package com.deepthoughtnet.clinic.api.notifications;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinic.notifications.scheduler")
public record NotificationsSchedulerProperties(
        boolean enabled,
        String fixedDelay
) {
}
