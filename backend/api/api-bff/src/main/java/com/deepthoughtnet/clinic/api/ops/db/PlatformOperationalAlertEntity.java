package com.deepthoughtnet.clinic.api.ops.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Tenant-scoped operational alert record for platform-native ops visibility. */
@Entity
@Table(name = "platform_operational_alerts", indexes = {
        @Index(name = "ix_platform_alerts_tenant_status_created", columnList = "tenant_id,status,created_at"),
        @Index(name = "ix_platform_alerts_source_created", columnList = "source,created_at"),
        @Index(name = "ix_platform_alerts_tenant_rule_status", columnList = "tenant_id,rule_key,status,last_seen_at")
})
public class PlatformOperationalAlertEntity {
    public enum Severity { WARNING, CRITICAL }
    public enum Status { OPEN, ACKNOWLEDGED, RESOLVED, SUPPRESSED }

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "rule_key", length = 120)
    private String ruleKey;

    @Column(name = "alert_type", nullable = false, length = 80)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false, length = 80)
    private String source;

    @Column(name = "source_entity_id", length = 160)
    private String sourceEntityId;

    @Column(name = "correlation_id", length = 160)
    private String correlationId;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "text")
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected PlatformOperationalAlertEntity() {
    }

    /** Creates a new open alert record for a specific rule/source tuple. */
    public static PlatformOperationalAlertEntity open(
            UUID tenantId,
            String ruleKey,
            String alertType,
            Severity severity,
            String source,
            String sourceEntityId,
            String correlationId,
            String message
    ) {
        PlatformOperationalAlertEntity entity = new PlatformOperationalAlertEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.ruleKey = ruleKey;
        entity.alertType = alertType;
        entity.severity = severity;
        entity.source = source;
        entity.sourceEntityId = sourceEntityId;
        entity.correlationId = correlationId;
        entity.message = message;
        entity.status = Status.OPEN;
        entity.occurrenceCount = 1;
        entity.createdAt = OffsetDateTime.now();
        entity.firstSeenAt = entity.createdAt;
        entity.lastSeenAt = entity.createdAt;
        return entity;
    }

    /** Increments frequency metadata on repeated incidents without creating noisy duplicate rows. */
    public void markRepeated(String newMessage, Severity newSeverity) {
        this.occurrenceCount += 1;
        this.lastSeenAt = OffsetDateTime.now();
        this.message = newMessage;
        this.severity = newSeverity;
        if (this.status == Status.RESOLVED) {
            this.status = Status.OPEN;
            this.resolvedAt = null;
            this.resolvedBy = null;
            this.resolutionNotes = null;
        }
    }

    public void acknowledge(UUID actorUserId) {
        this.status = Status.ACKNOWLEDGED;
        this.acknowledgedBy = actorUserId;
        this.acknowledgedAt = OffsetDateTime.now();
    }

    public void resolve(UUID actorUserId, String notes) {
        this.status = Status.RESOLVED;
        this.resolvedBy = actorUserId;
        this.resolutionNotes = notes;
        this.resolvedAt = OffsetDateTime.now();
    }

    public void suppress() {
        this.status = Status.SUPPRESSED;
        this.lastSeenAt = OffsetDateTime.now();
    }

    public void reopen(String message, Severity severity) {
        this.status = Status.OPEN;
        this.message = message;
        this.severity = severity;
        this.lastSeenAt = OffsetDateTime.now();
        this.resolvedAt = null;
        this.resolvedBy = null;
        this.resolutionNotes = null;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getRuleKey() { return ruleKey; }
    public String getAlertType() { return alertType; }
    public Severity getSeverity() { return severity; }
    public String getSource() { return source; }
    public String getSourceEntityId() { return sourceEntityId; }
    public String getCorrelationId() { return correlationId; }
    public String getMessage() { return message; }
    public Status getStatus() { return status; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public OffsetDateTime getFirstSeenAt() { return firstSeenAt; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public OffsetDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
    public String getResolutionNotes() { return resolutionNotes; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
}
