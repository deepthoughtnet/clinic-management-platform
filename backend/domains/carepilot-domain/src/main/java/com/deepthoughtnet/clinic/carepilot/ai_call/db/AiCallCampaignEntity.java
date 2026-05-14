package com.deepthoughtnet.clinic.carepilot.ai_call.db;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallCampaignStatus;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.AiCallType;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
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

/** Persistence entity for AI call campaign configuration. */
@Entity
@Table(name = "carepilot_ai_call_campaigns", indexes = {
        @Index(name = "ix_cp_ai_call_campaigns_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "ix_cp_ai_call_campaigns_tenant_type", columnList = "tenant_id,call_type")
})
public class AiCallCampaignEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false, length = 48)
    private AiCallType callType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AiCallCampaignStatus status;

    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChannelType channel;

    @Column(name = "retry_enabled", nullable = false)
    private boolean retryEnabled;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "escalation_enabled", nullable = false)
    private boolean escalationEnabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private int version;

    protected AiCallCampaignEntity() {}

    public static AiCallCampaignEntity create(UUID tenantId, UUID actorId) {
        AiCallCampaignEntity entity = new AiCallCampaignEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.callType = AiCallType.MANUAL_OUTREACH;
        entity.status = AiCallCampaignStatus.DRAFT;
        entity.channel = ChannelType.SMS;
        entity.retryEnabled = true;
        entity.maxAttempts = 3;
        entity.escalationEnabled = false;
        entity.createdBy = actorId;
        entity.updatedBy = actorId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void touch(UUID actorId) {
        this.updatedBy = actorId;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AiCallType getCallType() { return callType; }
    public void setCallType(AiCallType callType) { this.callType = callType; }
    public AiCallCampaignStatus getStatus() { return status; }
    public void setStatus(AiCallCampaignStatus status) { this.status = status; }
    public UUID getTemplateId() { return templateId; }
    public void setTemplateId(UUID templateId) { this.templateId = templateId; }
    public ChannelType getChannel() { return channel; }
    public void setChannel(ChannelType channel) { this.channel = channel; }
    public boolean isRetryEnabled() { return retryEnabled; }
    public void setRetryEnabled(boolean retryEnabled) { this.retryEnabled = retryEnabled; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public boolean isEscalationEnabled() { return escalationEnabled; }
    public void setEscalationEnabled(boolean escalationEnabled) { this.escalationEnabled = escalationEnabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
}
