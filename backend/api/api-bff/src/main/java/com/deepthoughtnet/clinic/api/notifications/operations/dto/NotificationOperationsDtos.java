package com.deepthoughtnet.clinic.api.notifications.operations.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotificationOperationsDtos {
    private NotificationOperationsDtos() {
    }

    public record NotificationOperationsQuery(
            UUID tenantId,
            String period,
            OffsetDateTime from,
            OffsetDateTime to,
            String status,
            String eventType,
            String channel,
            String channelStatus,
            String patientName,
            String patientReference,
            String businessReference,
            String provider,
            Boolean hasFailure,
            Boolean hasRetry,
            String sourceModule,
            String search,
            int page,
            int size
    ) {
    }

    public record NotificationOperationsSummaryResponse(
            UUID tenantId,
            String tenantName,
            String period,
            OffsetDateTime from,
            OffsetDateTime to,
            long logicalNotificationsCreated,
            long channelDeliveriesAttempted,
            long sentCount,
            long pendingCount,
            long failedCount,
            long skippedCount,
            long partialCount,
            double successRate,
            double averageDeliveryLatencyMs,
            long retryCount,
            long staleDeliveriesSuppressed,
            NotificationOperationsSchedulerStatus scheduler,
            List<NotificationOperationsKpiCard> kpis
    ) {
    }

    public record NotificationOperationsSchedulerStatus(
            boolean enabled,
            String fixedDelay,
            boolean appointmentReminderEnabled,
            int appointmentReminderHoursBefore,
            int appointmentReminderGraceMinutes,
            long outboxPendingCount,
            long outboxFailedCount
    ) {
    }

    public record NotificationOperationsKpiCard(
            String label,
            String value,
            String helper
    ) {
    }

    public record NotificationOperationsPageResponse(
            List<NotificationOperationsDeliveryRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record NotificationOperationsDeliveryRow(
            String logicalNotificationId,
            UUID tenantId,
            String tenantName,
            UUID patientId,
            String patientName,
            String patientReference,
            String eventType,
            String eventLabel,
            String category,
            String sourceModule,
            String businessReference,
            String overallStatus,
            String readState,
            String messagePreview,
            OffsetDateTime queuedAt,
            OffsetDateTime lastActivityAt,
            int retryCount,
            int deliveryCount,
            List<NotificationOperationsChannelRow> deliveries
    ) {
    }

    public record NotificationOperationsChannelRow(
            UUID id,
            String channel,
            String status,
            String displayStatus,
            String recipient,
            String provider,
            String failureReason,
            String failureCategory,
            boolean retryable,
            OffsetDateTime queuedAt,
            OffsetDateTime sentAt,
            int persistedAttemptCount,
            int deliveryAttemptCount,
            String providerReference
    ) {
    }

    public record NotificationOperationsProviderRow(
            String key,
            String name,
            String providerType,
            String configurationStatus,
            String readinessStatus,
            boolean enabled,
            boolean configured,
            long pendingCount,
            long failureCount,
            long successCount,
            double averageLatencyMs,
            OffsetDateTime lastSuccessfulAt,
            OffsetDateTime lastFailedAt,
            OffsetDateTime lastReadinessCheckAt,
            String message
    ) {
    }

    public record NotificationOperationsAnalyticsResponse(
            List<NotificationOperationsSeriesPoint> notificationsByDay,
            List<NotificationOperationsSeriesPoint> successFailureTrend,
            List<NotificationOperationsSeriesPoint> channelDistribution,
            List<NotificationOperationsSeriesPoint> statusDistribution,
            List<NotificationOperationsSeriesPoint> topCategories,
            List<NotificationOperationsSeriesPoint> topFailureReasons,
            List<NotificationOperationsSeriesPoint> providerPerformance,
            List<NotificationOperationsSeriesPoint> retryOutcomes
    ) {
    }

    public record NotificationOperationsSeriesPoint(
            String label,
            long value
    ) {
    }

    public record NotificationOperationsAuditResponse(
            List<NotificationOperationsAuditRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record NotificationOperationsAuditRow(
            UUID auditEventId,
            OffsetDateTime occurredAt,
            String actor,
            String tenantName,
            String action,
            String notificationId,
            String notificationType,
            String businessReference,
            String previousState,
            String newState,
            String reason,
            String result,
            String technicalDetails,
            Map<String, String> metadata
    ) {
    }

    public record NotificationOperationsRetryRequest(
            List<UUID> ids
    ) {
    }

    public record NotificationOperationsRetryResult(
            UUID id,
            String status,
            String message
    ) {
    }

    public record NotificationOperationsRetryResponse(
            List<NotificationOperationsRetryResult> results,
            int requestedCount,
            int retriedCount,
            int skippedCount
    ) {
    }
}
