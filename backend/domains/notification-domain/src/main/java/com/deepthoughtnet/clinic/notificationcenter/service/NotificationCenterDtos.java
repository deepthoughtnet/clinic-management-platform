package com.deepthoughtnet.clinic.notificationcenter.service;

import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationCategory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.NotificationPriority;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class NotificationCenterDtos {
    private NotificationCenterDtos() {
    }

    public record NotificationCenterQuery(
            String readState,
            String category,
            String priority,
            String search,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    ) {
    }

    public record NotificationCenterItem(
            UUID id,
            UUID notificationId,
            UUID tenantId,
            String title,
            String preview,
            NotificationCategory category,
            NotificationPriority priority,
            String businessReference,
            String sourceModule,
            String sourceEventType,
            String sourceEventLabel,
            String actionLabel,
            String actionRoute,
            UUID actionTargetId,
            String recipientDisplayName,
            String recipientRole,
            String matchedAudience,
            boolean read,
            OffsetDateTime readAt,
            OffsetDateTime occurredAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String correlationId,
            String causationId,
            long version
    ) {
    }

    public record NotificationCenterPage(
            List<NotificationCenterItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record NotificationCenterUnreadCount(
            long count
    ) {
    }

    public record NotificationCenterPreview(
            List<NotificationCenterItem> items
    ) {
    }
}
