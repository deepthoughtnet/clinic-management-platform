package com.deepthoughtnet.clinic.carepilot.campaign.db;

import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "carepilot_campaign_approval_history", indexes = {
        @Index(name = "ix_cp_campaign_approval_history_tenant_campaign", columnList = "tenant_id,campaign_id,created_at")
})
public class CampaignApprovalHistoryEntity {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "from_status", length = 24)
    @Convert(converter = CampaignStatusConverter.class)
    private CampaignStatus fromStatus;

    @Column(name = "to_status", nullable = false, length = 24)
    @Convert(converter = CampaignStatusConverter.class)
    private CampaignStatus toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 64)
    private String actorRole;

    @Column(name = "actor_display_name", length = 256)
    private String actorDisplayName;

    @Column(name = "actor_role_label", length = 128)
    private String actorRoleLabel;

    @Column(name = "actor_employee_code", length = 64)
    private String actorEmployeeCode;

    @Column(name = "actor_username", length = 128)
    private String actorUsername;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "invalidation_reason", columnDefinition = "text")
    private String invalidationReason;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "previous_campaign_version")
    private Integer previousCampaignVersion;

    @Column(name = "campaign_version")
    private Integer campaignVersion;

    @Column(name = "new_campaign_version")
    private Integer newCampaignVersion;

    @Column(name = "previous_configuration_hash", length = 128)
    private String previousConfigurationHash;

    @Column(name = "configuration_hash", length = 128)
    private String configurationHash;

    @Column(name = "new_configuration_hash", length = 128)
    private String newConfigurationHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CampaignApprovalHistoryEntity() {}

    public static CampaignApprovalHistoryEntity create(
            UUID tenantId,
            UUID campaignId,
            String eventType,
            CampaignStatus fromStatus,
            CampaignStatus toStatus,
            UUID actorId,
            String actorRole,
            String actorDisplayName,
            String actorRoleLabel,
            String actorEmployeeCode,
            String actorUsername,
            String comment,
            String invalidationReason,
            String resolutionNote,
            Integer previousCampaignVersion,
            Integer campaignVersion,
            Integer newCampaignVersion,
            String previousConfigurationHash,
            String configurationHash,
            String newConfigurationHash
    ) {
        CampaignApprovalHistoryEntity entity = new CampaignApprovalHistoryEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = tenantId;
        entity.campaignId = campaignId;
        entity.eventType = eventType;
        entity.fromStatus = fromStatus;
        entity.toStatus = toStatus;
        entity.actorId = actorId;
        entity.actorRole = actorRole;
        entity.actorDisplayName = actorDisplayName;
        entity.actorRoleLabel = actorRoleLabel;
        entity.actorEmployeeCode = actorEmployeeCode;
        entity.actorUsername = actorUsername;
        entity.comment = comment;
        entity.invalidationReason = invalidationReason;
        entity.resolutionNote = resolutionNote;
        entity.previousCampaignVersion = previousCampaignVersion;
        entity.campaignVersion = campaignVersion;
        entity.newCampaignVersion = newCampaignVersion;
        entity.previousConfigurationHash = previousConfigurationHash;
        entity.configurationHash = configurationHash;
        entity.newConfigurationHash = newConfigurationHash;
        entity.createdAt = OffsetDateTime.now();
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCampaignId() { return campaignId; }
    public String getEventType() { return eventType; }
    public CampaignStatus getFromStatus() { return fromStatus; }
    public CampaignStatus getToStatus() { return toStatus; }
    public UUID getActorId() { return actorId; }
    public String getActorRole() { return actorRole; }
    public String getActorDisplayName() { return actorDisplayName; }
    public String getActorRoleLabel() { return actorRoleLabel; }
    public String getActorEmployeeCode() { return actorEmployeeCode; }
    public String getActorUsername() { return actorUsername; }
    public String getComment() { return comment; }
    public String getInvalidationReason() { return invalidationReason; }
    public String getResolutionNote() { return resolutionNote; }
    public Integer getPreviousCampaignVersion() { return previousCampaignVersion; }
    public Integer getCampaignVersion() { return campaignVersion; }
    public Integer getNewCampaignVersion() { return newCampaignVersion; }
    public String getPreviousConfigurationHash() { return previousConfigurationHash; }
    public String getConfigurationHash() { return configurationHash; }
    public String getNewConfigurationHash() { return newConfigurationHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
