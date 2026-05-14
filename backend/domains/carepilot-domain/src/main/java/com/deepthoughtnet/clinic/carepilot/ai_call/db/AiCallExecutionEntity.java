package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Persistence entity for each AI call execution attempt lifecycle. */
@Entity
@Table(name = "carepilot_ai_call_executions", indexes = {
        @Index(name = "ix_cp_ai_call_exec_tenant_status", columnList = "tenant_id,execution_status"),
        @Index(name = "ix_cp_ai_call_exec_tenant_scheduled", columnList = "tenant_id,scheduled_at"),
        @Index(name = "ix_cp_ai_call_exec_tenant_campaign", columnList = "tenant_id,campaign_id"),
        @Index(name = "ix_cp_ai_call_exec_tenant_escalation", columnList = "tenant_id,escalation_required")
})
public class AiCallExecutionEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "phone_number", nullable = false, length = 48)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_status", nullable = false, length = 32)
    private AiCallExecutionStatus executionStatus;

    @Column(name = "provider_name", length = 64)
    private String providerName;

    @Column(name = "provider_call_id", length = 128)
    private String providerCallId;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "suppression_reason", length = 64)
    private String suppressionReason;

    @Column(name = "escalation_required", nullable = false)
    private boolean escalationRequired;

    @Column(name = "escalation_reason", columnDefinition = "text")
    private String escalationReason;

    @Column(name = "failover_attempted", nullable = false)
    private boolean failoverAttempted;

    @Column(name = "failover_reason", columnDefinition = "text")
    private String failoverReason;

    @Column(name = "transcript_id")
    private UUID transcriptId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected AiCallExecutionEntity() {}

    public static AiCallExecutionEntity create(
            UUID tenantId,
            UUID campaignId,
            UUID patientId,
            UUID leadId,
            String phoneNumber,
            OffsetDateTime scheduledAt
    ) {
        AiCallExecutionEntity entity = new AiCallExecutionEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.campaignId = campaignId;
        entity.patientId = patientId;
        entity.leadId = leadId;
        entity.phoneNumber = phoneNumber;
        entity.executionStatus = AiCallExecutionStatus.PENDING;
        entity.scheduledAt = scheduledAt == null ? OffsetDateTime.now() : scheduledAt;
        entity.retryCount = 0;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getPatientId() { return patientId; }
    public UUID getLeadId() { return leadId; }
    public String getPhoneNumber() { return phoneNumber; }
    public AiCallExecutionStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(AiCallExecutionStatus executionStatus) { this.executionStatus = executionStatus; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getProviderCallId() { return providerCallId; }
    public void setProviderCallId(String providerCallId) { this.providerCallId = providerCallId; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public OffsetDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(OffsetDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public OffsetDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(OffsetDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getSuppressionReason() { return suppressionReason; }
    public void setSuppressionReason(String suppressionReason) { this.suppressionReason = suppressionReason; }
    public boolean isEscalationRequired() { return escalationRequired; }
    public void setEscalationRequired(boolean escalationRequired) { this.escalationRequired = escalationRequired; }
    public String getEscalationReason() { return escalationReason; }
    public void setEscalationReason(String escalationReason) { this.escalationReason = escalationReason; }
    public boolean isFailoverAttempted() { return failoverAttempted; }
    public void setFailoverAttempted(boolean failoverAttempted) { this.failoverAttempted = failoverAttempted; }
    public String getFailoverReason() { return failoverReason; }
    public void setFailoverReason(String failoverReason) { this.failoverReason = failoverReason; }
    public UUID getTranscriptId() { return transcriptId; }
    public void setTranscriptId(UUID transcriptId) { this.transcriptId = transcriptId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
