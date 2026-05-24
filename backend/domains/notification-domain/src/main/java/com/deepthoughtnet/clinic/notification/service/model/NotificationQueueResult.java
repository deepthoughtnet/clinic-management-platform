package com.deepthoughtnet.clinic.notification.service.model;

public record NotificationQueueResult(
        NotificationHistoryRecord notification,
        boolean created
) {
}
