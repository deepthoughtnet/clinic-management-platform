package com.deepthoughtnet.clinic.carepilot.campaign.db;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
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

/**
 * Persistence model for tenant-scoped CarePilot campaign definitions.
 */
@Entity
@Table(name = "carepilot_campaigns", indexes = {
        @Index(name = "ix_cp_campaigns_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "ix_cp_campaigns_tenant_created", columnList = "tenant_id,created_at")
})
public class CampaignEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 140)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false, length = 40)
    private CampaignType campaignType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CampaignStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 24)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 24)
    private AudienceType audienceType;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected CampaignEntity() {}

    public static CampaignEntity create(UUID tenantId, String name, CampaignType type, TriggerType triggerType, AudienceType audienceType, UUID templateId, String notes, UUID actorId) {
        CampaignEntity entity = new CampaignEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.name = name;
        entity.campaignType = type;
        entity.status = CampaignStatus.DRAFT;
        entity.triggerType = triggerType;
        entity.audienceType = audienceType;
        entity.templateId = templateId;
        entity.active = false;
        entity.notes = notes;
        entity.createdBy = actorId;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void activate() { this.active = true; this.status = CampaignStatus.ACTIVE; this.updatedAt = OffsetDateTime.now(); }
    public void deactivate() { this.active = false; this.status = CampaignStatus.INACTIVE; this.updatedAt = OffsetDateTime.now(); }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public CampaignType getCampaignType() { return campaignType; }
    public CampaignStatus getStatus() { return status; }
    public TriggerType getTriggerType() { return triggerType; }
    public AudienceType getAudienceType() { return audienceType; }
    public UUID getTemplateId() { return templateId; }
    public boolean isActive() { return active; }
    public String getNotes() { return notes; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
