package com.deepthoughtnet.clinic.carepilot.template.db;

import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Template content managed per tenant for future provider dispatch. */
@Entity
@Table(name = "carepilot_campaign_templates", indexes = {
        @Index(name = "ix_cp_templates_tenant_created", columnList = "tenant_id,created_at")
})
public class CampaignTemplateEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 140)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false, length = 24)
    private ChannelType channelType;

    @Column(name = "subject_line", length = 180)
    private String subjectLine;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CampaignTemplateEntity() {}

    public static CampaignTemplateEntity create(UUID tenantId, String name, ChannelType channelType, String subjectLine, String bodyTemplate, boolean active) {
        CampaignTemplateEntity entity = new CampaignTemplateEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.name = name;
        entity.channelType = channelType;
        entity.subjectLine = subjectLine;
        entity.bodyTemplate = bodyTemplate;
        entity.active = active;
        entity.createdAt = OffsetDateTime.now();
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    public void patch(String name, String subjectLine, String bodyTemplate, Boolean active) {
        if (name != null) { this.name = name; }
        if (subjectLine != null) { this.subjectLine = subjectLine; }
        if (bodyTemplate != null) { this.bodyTemplate = bodyTemplate; }
        if (active != null) { this.active = active; }
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getName() { return name; }
    public ChannelType getChannelType() { return channelType; }
    public String getSubjectLine() { return subjectLine; }
    public String getBodyTemplate() { return bodyTemplate; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
