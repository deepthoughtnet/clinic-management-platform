package com.deepthoughtnet.clinic.notification.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_outbox",
        indexes = {
                @Index(name = "ix_notification_outbox_status_next", columnList = "status,next_attempt_at"),
                @Index(name = "ix_notification_outbox_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "ix_notification_outbox_aggregate", columnList = "aggregate_type,aggregate_id"),
                @Index(name = "ix_notification_outbox_tenant_module", columnList = "tenant_id,module"),
                @Index(name = "ix_notification_outbox_tenant_event_type", columnList = "tenant_id,event_type"),
                @Index(name = "ix_notification_outbox_entity", columnList = "entity_type,entity_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_notification_outbox_dedup", columnNames = {"deduplication_key"})
        }
)
public class NotificationOutboxEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "module", length = 64)
    private String module;

    @Column(name = "entity_type", length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "deduplication_key", nullable = false, length = 256)
    private String deduplicationKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "ignored_at")
    private OffsetDateTime ignoredAt;

    @Column(name = "ignored_by_app_user_id")
    private UUID ignoredByAppUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected NotificationOutboxEntity() {
    }

    public static NotificationOutboxEntity pending(
            UUID tenantId,
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String deduplicationKey,
            String payloadJson,
            OffsetDateTime availableAt
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        NotificationOutboxEntity entity = new NotificationOutboxEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.eventType = eventType;
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.module = moduleFromAggregateType(aggregateType);
        entity.entityType = aggregateType;
        entity.entityId = aggregateId;
        entity.deduplicationKey = deduplicationKey;
        entity.payloadJson = payloadJson;
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = availableAt == null ? now : availableAt;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getModule() { return module; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public OffsetDateTime getIgnoredAt() { return ignoredAt; }
    public UUID getIgnoredByAppUserId() { return ignoredByAppUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

    public void markProcessing() {
        this.status = "PROCESSING";
        this.attemptCount = this.attemptCount + 1;
        this.lastError = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markSucceeded() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = "SENT";
        this.processedAt = now;
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markFailed(String errorMessage, int maxAttempts, Duration backoff) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = attemptCount >= maxAttempts ? "FAILED" : "PENDING";
        this.nextAttemptAt = "PENDING".equals(this.status)
                ? now.plus(backoff.multipliedBy(Math.max(1, attemptCount)))
                : null;
        this.lastError = errorMessage;
        this.updatedAt = now;
    }

    public void retryNow() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = "PENDING";
        this.nextAttemptAt = now;
        this.processedAt = null;
        this.ignoredAt = null;
        this.ignoredByAppUserId = null;
        this.updatedAt = now;
    }

    public void markIgnored(UUID ignoredByAppUserId) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = "IGNORED";
        this.nextAttemptAt = null;
        this.ignoredAt = now;
        this.ignoredByAppUserId = ignoredByAppUserId;
        this.updatedAt = now;
    }

    private static String moduleFromAggregateType(String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return "unknown";
        }
        return aggregateType.startsWith("CLINIC") ? "clinic" : aggregateType.toLowerCase();
    }
}
