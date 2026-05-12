package com.deepthoughtnet.clinic.carepilot.execution.db;

import com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.messaging.spi.MessageDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Execution ledger entry used by scheduler-safe dispatch orchestration.
 */
@Entity
@Table(name = "carepilot_campaign_executions", indexes = {
        @Index(name = "ix_cp_exec_tenant_status_scheduled", columnList = "tenant_id,status,scheduled_at"),
        @Index(name = "ix_cp_exec_tenant_campaign", columnList = "tenant_id,campaign_id")
})
public class CampaignExecutionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 24)
    private ChannelType channelType;

    @Column(name = "recipient_patient_id")
    private UUID recipientPatientId;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExecutionStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "executed_at")
    private OffsetDateTime executedAt;

    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 40)
    private MessageDeliveryStatus deliveryStatus;

    @Column(name = "provider_name", length = 80)
    private String providerName;

    @Column(name = "provider_message_id", length = 180)
    private String providerMessageId;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CampaignExecutionEntity() {}

    public static CampaignExecutionEntity create(UUID tenantId, UUID campaignId, UUID templateId, ChannelType channelType, UUID recipientPatientId, OffsetDateTime scheduledAt) {
        CampaignExecutionEntity entity = new CampaignExecutionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.campaignId = campaignId;
        entity.templateId = templateId;
        entity.channelType = channelType;
        entity.recipientPatientId = recipientPatientId;
        entity.scheduledAt = scheduledAt;
        entity.status = ExecutionStatus.QUEUED;
        entity.attemptCount = 0;
        entity.deliveryStatus = MessageDeliveryStatus.SKIPPED;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void markProcessing() {
        this.status = ExecutionStatus.PROCESSING;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markSucceeded(String providerName, String providerMessageId) {
        this.status = ExecutionStatus.SUCCEEDED;
        this.executedAt = OffsetDateTime.now();
        this.lastAttemptAt = this.executedAt;
        this.lastError = null;
        this.failureReason = null;
        this.nextAttemptAt = null;
        this.deliveryStatus = MessageDeliveryStatus.SENT;
        this.providerName = providerName;
        this.providerMessageId = providerMessageId;
        this.updatedAt = this.executedAt;
    }

    public void markFailed(String error, String failureReason, MessageDeliveryStatus deliveryStatus, OffsetDateTime nextAttemptAt, int maxRetries) {
        this.attemptCount += 1;
        this.lastError = error;
        this.failureReason = failureReason;
        this.deliveryStatus = deliveryStatus;
        this.lastAttemptAt = OffsetDateTime.now();
        this.nextAttemptAt = nextAttemptAt;
        if (nextAttemptAt != null && this.attemptCount < maxRetries) {
            this.status = ExecutionStatus.RETRY_SCHEDULED;
        } else if (this.attemptCount >= maxRetries) {
            this.status = ExecutionStatus.DEAD_LETTER;
            this.nextAttemptAt = null;
        } else {
            this.status = ExecutionStatus.FAILED;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public void markQueuedForRetry() {
        this.status = ExecutionStatus.QUEUED;
        this.scheduledAt = OffsetDateTime.now();
        this.nextAttemptAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getTemplateId() { return templateId; }
    public ChannelType getChannelType() { return channelType; }
    public UUID getRecipientPatientId() { return recipientPatientId; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public ExecutionStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public OffsetDateTime getExecutedAt() { return executedAt; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public MessageDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public String getProviderName() { return providerName; }
    public String getProviderMessageId() { return providerMessageId; }
    public OffsetDateTime getLastAttemptAt() { return lastAttemptAt; }
    public String getFailureReason() { return failureReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
