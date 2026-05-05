package com.deepthoughtnet.clinic.notification.service;

import java.time.OffsetDateTime;

public record NotificationSummary(
        long pendingCount,
        long failedCount,
        long sentTodayCount,
        long ignoredCount,
        OffsetDateTime lastFailedAt
) {
}
