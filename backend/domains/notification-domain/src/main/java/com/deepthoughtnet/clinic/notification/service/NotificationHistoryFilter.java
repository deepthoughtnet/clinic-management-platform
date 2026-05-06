package com.deepthoughtnet.clinic.notification.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationHistoryFilter(
        String status,
        String eventType,
        String channel,
        UUID patientId,
        OffsetDateTime from,
        OffsetDateTime to,
        int page,
        int size
) {
}
