package com.deepthoughtnet.clinic.notification.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_history",
        indexes = {
                @Index(name = "ix_notification_history_tenant_created", columnList = "tenant_id,created_at"),
                @Index(name = "ix_notification_history_tenant_patient", columnList = "tenant_id,patient_id"),
                @Index(name = "ix_notification_history_tenant_event", columnList = "tenant_id,event_type"),
                @Index(name = "ix_notification_history_tenant_status", columnList = "tenant_id,status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_notification_history_dedup", columnNames = {"tenant_id", "deduplication_key"})
        }
)
public class NotificationHistoryEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(nullable = false, length = 256)
    private String recipient;

    @Column(length = 256)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "deduplication_key", nullable = false, length = 256)
    private String deduplicationKey;

    @Column(name = "outbox_event_id")
    private UUID outboxEventId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected NotificationHistoryEntity() {
    }

    public static NotificationHistoryEntity create(
            UUID tenantId,
            UUID patientId,
            String eventType,
            String channel,
            String recipient,
            String subject,
            String message,
            String sourceType,
            UUID sourceId,
            String deduplicationKey
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        NotificationHistoryEntity entity = new NotificationHistoryEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.patientId = patientId;
        entity.eventType = eventType;
        entity.channel = channel;
        entity.recipient = recipient;
        entity.subject = subject;
        entity.message = message;
        entity.status = "PENDING";
        entity.sourceType = sourceType;
        entity.sourceId = sourceId;
        entity.deduplicationKey = deduplicationKey;
        entity.attemptCount = 0;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getPatientId() { return patientId; }
    public String getEventType() { return eventType; }
    public String getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public UUID getOutboxEventId() { return outboxEventId; }
    public int getAttemptCount() { return attemptCount; }
    public OffsetDateTime getSentAt() { return sentAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

    public void markSent() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = "SENT";
        this.sentAt = now;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void markFailed(String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = "FAILED";
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void markSkipped(String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = "SKIPPED";
        this.failureReason = reason;
        this.updatedAt = now;
    }

    public void retryNow() {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = "PENDING";
        this.failureReason = null;
        this.attemptCount = this.attemptCount + 1;
        this.updatedAt = now;
    }

    public void attachOutboxEvent(UUID outboxEventId) {
        this.outboxEventId = outboxEventId;
        this.updatedAt = OffsetDateTime.now();
    }
}
