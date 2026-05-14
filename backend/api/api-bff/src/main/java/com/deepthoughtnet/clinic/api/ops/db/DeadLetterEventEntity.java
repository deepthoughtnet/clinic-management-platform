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

/** Dead-letter record for permanently failed operational executions. */
@Entity
@Table(name = "platform_dead_letter_events", indexes = {
        @Index(name = "ix_dlq_tenant_source_created", columnList = "tenant_id,source_type,dead_lettered_at"),
        @Index(name = "ix_dlq_tenant_recovery", columnList = "tenant_id,recovery_status,dead_lettered_at")
})
public class DeadLetterEventEntity {
    public enum SourceType { CAMPAIGN_EXECUTION, AI_CALL_EXECUTION }
    public enum RecoveryStatus { PENDING, REPLAYED, REPLAY_FAILED }

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private SourceType sourceType;

    @Column(name = "source_execution_id", nullable = false)
    private UUID sourceExecutionId;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "payload_summary", columnDefinition = "text")
    private String payloadSummary;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "dead_lettered_at", nullable = false)
    private OffsetDateTime deadLetteredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_status", nullable = false, length = 30)
    private RecoveryStatus recoveryStatus;

    @Column(name = "last_recovery_error", columnDefinition = "text")
    private String lastRecoveryError;

    protected DeadLetterEventEntity() {}

    public static DeadLetterEventEntity create(
            UUID tenantId,
            SourceType sourceType,
            UUID sourceExecutionId,
            String failureReason,
            String payloadSummary,
            int retryCount
    ) {
        DeadLetterEventEntity row = new DeadLetterEventEntity();
        row.id = UUID.randomUUID();
        row.tenantId = tenantId;
        row.sourceType = sourceType;
        row.sourceExecutionId = sourceExecutionId;
        row.failureReason = failureReason;
        row.payloadSummary = payloadSummary;
        row.retryCount = retryCount;
        row.deadLetteredAt = OffsetDateTime.now();
        row.recoveryStatus = RecoveryStatus.PENDING;
        return row;
    }

    public void markReplayed() {
        this.recoveryStatus = RecoveryStatus.REPLAYED;
        this.lastRecoveryError = null;
    }

    public void markReplayFailed(String error) {
        this.recoveryStatus = RecoveryStatus.REPLAY_FAILED;
        this.lastRecoveryError = error;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public SourceType getSourceType() { return sourceType; }
    public UUID getSourceExecutionId() { return sourceExecutionId; }
    public String getFailureReason() { return failureReason; }
    public String getPayloadSummary() { return payloadSummary; }
    public int getRetryCount() { return retryCount; }
    public OffsetDateTime getDeadLetteredAt() { return deadLetteredAt; }
    public RecoveryStatus getRecoveryStatus() { return recoveryStatus; }
    public String getLastRecoveryError() { return lastRecoveryError; }
}
