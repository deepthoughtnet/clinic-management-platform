package com.deepthoughtnet.clinic.carepilot.lead.activity.db;

import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only persistence model for lead timeline activities. */
@Entity
@Table(name = "carepilot_lead_activities", indexes = {
        @Index(name = "ix_cp_lead_activities_tenant_lead_created", columnList = "tenant_id,lead_id,created_at"),
        @Index(name = "ix_cp_lead_activities_tenant_type", columnList = "tenant_id,activity_type"),
        @Index(name = "ix_cp_lead_activities_tenant_created", columnList = "tenant_id,created_at")
})
public class LeadActivityEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "lead_id", nullable = false)
    private UUID leadId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 48)
    private LeadActivityType activityType;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 32)
    private LeadStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 32)
    private LeadStatus newStatus;

    @Column(name = "related_entity_type", length = 48)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "created_by_app_user_id")
    private UUID createdByAppUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected LeadActivityEntity() {}

    public static LeadActivityEntity create(
            UUID tenantId,
            UUID leadId,
            LeadActivityType activityType,
            String title,
            String description,
            LeadStatus oldStatus,
            LeadStatus newStatus,
            String relatedEntityType,
            UUID relatedEntityId,
            UUID createdByAppUserId
    ) {
        LeadActivityEntity entity = new LeadActivityEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.leadId = leadId;
        entity.activityType = activityType;
        entity.title = title;
        entity.description = description;
        entity.oldStatus = oldStatus;
        entity.newStatus = newStatus;
        entity.relatedEntityType = relatedEntityType;
        entity.relatedEntityId = relatedEntityId;
        entity.createdByAppUserId = createdByAppUserId;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getLeadId() { return leadId; }
    public LeadActivityType getActivityType() { return activityType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LeadStatus getOldStatus() { return oldStatus; }
    public LeadStatus getNewStatus() { return newStatus; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public UUID getRelatedEntityId() { return relatedEntityId; }
    public UUID getCreatedByAppUserId() { return createdByAppUserId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
