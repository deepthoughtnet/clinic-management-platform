package com.deepthoughtnet.clinic.carepilot.campaign.db;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatusConverter;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

    @Column(name = "campaign_reference", nullable = false, length = 32)
    private String campaignReference;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(name = "campaign_type", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private CampaignType campaignType;

    @Column(nullable = false, length = 24)
    @Convert(converter = CampaignStatusConverter.class)
    private CampaignStatus status;

    @Column(name = "trigger_type", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    @Column(name = "audience_type", nullable = false, length = 24)
    @Enumerated(EnumType.STRING)
    private AudienceType audienceType;

    @Column(name = "template_id")
    private UUID templateId;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_by_display_name", length = 256)
    private String submittedByDisplayName;

    @Column(name = "submitted_by_role_label", length = 128)
    private String submittedByRoleLabel;

    @Column(name = "submitted_by_employee_code", length = 64)
    private String submittedByEmployeeCode;

    @Column(name = "submitted_by_username", length = 128)
    private String submittedByUsername;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_by_display_name", length = 256)
    private String reviewedByDisplayName;

    @Column(name = "reviewed_by_role_label", length = 128)
    private String reviewedByRoleLabel;

    @Column(name = "reviewed_by_employee_code", length = 64)
    private String reviewedByEmployeeCode;

    @Column(name = "reviewed_by_username", length = 128)
    private String reviewedByUsername;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_comment", columnDefinition = "text")
    private String reviewComment;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_by_display_name", length = 256)
    private String approvedByDisplayName;

    @Column(name = "approved_by_role_label", length = 128)
    private String approvedByRoleLabel;

    @Column(name = "approved_by_employee_code", length = 64)
    private String approvedByEmployeeCode;

    @Column(name = "approved_by_username", length = 128)
    private String approvedByUsername;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "activation_by")
    private UUID activationBy;

    @Column(name = "activation_by_display_name", length = 256)
    private String activationByDisplayName;

    @Column(name = "activation_by_role_label", length = 128)
    private String activationByRoleLabel;

    @Column(name = "activation_by_employee_code", length = 64)
    private String activationByEmployeeCode;

    @Column(name = "activation_by_username", length = 128)
    private String activationByUsername;

    @Column(name = "activation_at")
    private OffsetDateTime activationAt;

    @Column(name = "approval_invalidated_reason", columnDefinition = "text")
    private String approvalInvalidatedReason;

    @Column(name = "approved_version")
    private Integer approvedVersion;

    @Column(name = "approved_configuration_hash", length = 128)
    private String approvedConfigurationHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected CampaignEntity() {}

    public static CampaignEntity create(UUID tenantId, String campaignReference, String name, CampaignType type, TriggerType triggerType, AudienceType audienceType, UUID templateId, String notes, UUID actorId) {
        CampaignEntity entity = new CampaignEntity();
        entity.id = UUID.randomUUID();
        entity.campaignReference = campaignReference;
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

    public static CampaignEntity create(UUID tenantId, String name, CampaignType type, TriggerType triggerType, AudienceType audienceType, UUID templateId, String notes, UUID actorId) {
        return create(tenantId, null, name, type, triggerType, audienceType, templateId, notes, actorId);
    }

    public void updateDraft(String name, CampaignType type, TriggerType triggerType, AudienceType audienceType, UUID templateId, String notes) {
        this.name = name;
        this.campaignType = type;
        this.triggerType = triggerType;
        this.audienceType = audienceType;
        this.templateId = templateId;
        this.notes = notes;
        this.updatedAt = OffsetDateTime.now();
    }

    public void submit(UUID submittedBy, ActorSnapshot snapshot, int version, String configurationHash) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = CampaignStatus.PENDING_APPROVAL;
        this.submittedBy = submittedBy;
        applySubmittedSnapshot(snapshot);
        this.submittedAt = now;
        this.reviewedBy = null;
        this.reviewedByDisplayName = null;
        this.reviewedByRoleLabel = null;
        this.reviewedByEmployeeCode = null;
        this.reviewedByUsername = null;
        this.reviewedAt = null;
        this.reviewComment = null;
        this.approvedBy = null;
        this.approvedByDisplayName = null;
        this.approvedByRoleLabel = null;
        this.approvedByEmployeeCode = null;
        this.approvedByUsername = null;
        this.approvedAt = null;
        this.activationBy = null;
        this.activationByDisplayName = null;
        this.activationByRoleLabel = null;
        this.activationByEmployeeCode = null;
        this.activationByUsername = null;
        this.activationAt = null;
        this.approvalInvalidatedReason = null;
        this.approvedVersion = version;
        this.approvedConfigurationHash = configurationHash;
        this.updatedAt = now;
    }

    public void withdraw(UUID actorId) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = CampaignStatus.DRAFT;
        this.submittedBy = null;
        this.submittedByDisplayName = null;
        this.submittedByRoleLabel = null;
        this.submittedByEmployeeCode = null;
        this.submittedByUsername = null;
        this.submittedAt = null;
        this.reviewedBy = null;
        this.reviewedByDisplayName = null;
        this.reviewedByRoleLabel = null;
        this.reviewedByEmployeeCode = null;
        this.reviewedByUsername = null;
        this.reviewedAt = null;
        this.reviewComment = null;
        this.approvedBy = null;
        this.approvedByDisplayName = null;
        this.approvedByRoleLabel = null;
        this.approvedByEmployeeCode = null;
        this.approvedByUsername = null;
        this.approvedAt = null;
        this.activationBy = null;
        this.activationByDisplayName = null;
        this.activationByRoleLabel = null;
        this.activationByEmployeeCode = null;
        this.activationByUsername = null;
        this.activationAt = null;
        this.approvalInvalidatedReason = null;
        this.approvedVersion = null;
        this.approvedConfigurationHash = null;
        this.updatedAt = now;
    }

    public void requestChanges(UUID reviewedBy, ActorSnapshot snapshot, String reviewComment) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = CampaignStatus.CHANGES_REQUESTED;
        this.reviewedBy = reviewedBy;
        applyReviewedSnapshot(snapshot);
        this.reviewedAt = now;
        this.reviewComment = reviewComment;
        this.approvedBy = null;
        this.approvedByDisplayName = null;
        this.approvedByRoleLabel = null;
        this.approvedByEmployeeCode = null;
        this.approvedByUsername = null;
        this.approvedAt = null;
        this.activationBy = null;
        this.activationByDisplayName = null;
        this.activationByRoleLabel = null;
        this.activationByEmployeeCode = null;
        this.activationByUsername = null;
        this.activationAt = null;
        this.updatedAt = now;
    }

    public void approve(UUID reviewedBy, ActorSnapshot snapshot, String reviewComment, int version, String configurationHash) {
        OffsetDateTime now = OffsetDateTime.now();
        this.status = CampaignStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        applyReviewedSnapshot(snapshot);
        this.reviewedAt = now;
        this.reviewComment = reviewComment;
        this.approvedBy = reviewedBy;
        applyApprovedSnapshot(snapshot);
        this.approvedAt = now;
        this.activationBy = null;
        this.activationByDisplayName = null;
        this.activationByRoleLabel = null;
        this.activationByEmployeeCode = null;
        this.activationByUsername = null;
        this.activationAt = null;
        this.approvalInvalidatedReason = null;
        this.approvedVersion = version;
        this.approvedConfigurationHash = configurationHash;
        this.updatedAt = now;
    }

    public void activate(UUID activationBy, ActorSnapshot snapshot) {
        OffsetDateTime now = OffsetDateTime.now();
        this.active = true;
        this.status = CampaignStatus.ACTIVE;
        this.activationBy = activationBy;
        applyActivationSnapshot(snapshot);
        this.activationAt = now;
        this.updatedAt = now;
    }

    public void activate() {
        activate(null, null);
    }

    public void pause() {
        this.active = false;
        this.status = CampaignStatus.PAUSED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void resume() {
        this.active = true;
        this.status = CampaignStatus.ACTIVE;
        this.updatedAt = OffsetDateTime.now();
    }

    public void deactivate() {
        pause();
    }

    public void complete() {
        this.active = false;
        this.status = CampaignStatus.COMPLETED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void cancel() {
        this.active = false;
        this.status = CampaignStatus.CANCELLED;
        this.updatedAt = OffsetDateTime.now();
    }

    public void invalidateApproval(String reason) {
        this.approvalInvalidatedReason = reason;
        if (this.status == CampaignStatus.APPROVED || this.status == CampaignStatus.ACTIVE) {
            this.status = CampaignStatus.CHANGES_REQUESTED;
        }
        this.approvedBy = null;
        this.approvedByDisplayName = null;
        this.approvedByRoleLabel = null;
        this.approvedByEmployeeCode = null;
        this.approvedByUsername = null;
        this.approvedAt = null;
        this.activationBy = null;
        this.activationByDisplayName = null;
        this.activationByRoleLabel = null;
        this.activationByEmployeeCode = null;
        this.activationByUsername = null;
        this.activationAt = null;
        this.approvedVersion = null;
        this.approvedConfigurationHash = null;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getCampaignReference() { return campaignReference; }
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
    public UUID getSubmittedBy() { return submittedBy; }
    public String getSubmittedByDisplayName() { return submittedByDisplayName; }
    public String getSubmittedByRoleLabel() { return submittedByRoleLabel; }
    public String getSubmittedByEmployeeCode() { return submittedByEmployeeCode; }
    public String getSubmittedByUsername() { return submittedByUsername; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public String getReviewedByDisplayName() { return reviewedByDisplayName; }
    public String getReviewedByRoleLabel() { return reviewedByRoleLabel; }
    public String getReviewedByEmployeeCode() { return reviewedByEmployeeCode; }
    public String getReviewedByUsername() { return reviewedByUsername; }
    public OffsetDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public UUID getApprovedBy() { return approvedBy; }
    public String getApprovedByDisplayName() { return approvedByDisplayName; }
    public String getApprovedByRoleLabel() { return approvedByRoleLabel; }
    public String getApprovedByEmployeeCode() { return approvedByEmployeeCode; }
    public String getApprovedByUsername() { return approvedByUsername; }
    public OffsetDateTime getApprovedAt() { return approvedAt; }
    public UUID getActivationBy() { return activationBy; }
    public String getActivationByDisplayName() { return activationByDisplayName; }
    public String getActivationByRoleLabel() { return activationByRoleLabel; }
    public String getActivationByEmployeeCode() { return activationByEmployeeCode; }
    public String getActivationByUsername() { return activationByUsername; }
    public OffsetDateTime getActivationAt() { return activationAt; }
    public String getApprovalInvalidatedReason() { return approvalInvalidatedReason; }
    public Integer getApprovedVersion() { return approvedVersion; }
    public String getApprovedConfigurationHash() { return approvedConfigurationHash; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

    private void applySubmittedSnapshot(ActorSnapshot snapshot) {
        if (snapshot == null) {
            this.submittedByDisplayName = null;
            this.submittedByRoleLabel = null;
            this.submittedByEmployeeCode = null;
            this.submittedByUsername = null;
            return;
        }
        this.submittedByDisplayName = snapshot.displayName();
        this.submittedByRoleLabel = snapshot.roleLabel();
        this.submittedByEmployeeCode = snapshot.employeeCode();
        this.submittedByUsername = snapshot.username();
    }

    private void applyReviewedSnapshot(ActorSnapshot snapshot) {
        if (snapshot == null) {
            this.reviewedByDisplayName = null;
            this.reviewedByRoleLabel = null;
            this.reviewedByEmployeeCode = null;
            this.reviewedByUsername = null;
            return;
        }
        this.reviewedByDisplayName = snapshot.displayName();
        this.reviewedByRoleLabel = snapshot.roleLabel();
        this.reviewedByEmployeeCode = snapshot.employeeCode();
        this.reviewedByUsername = snapshot.username();
    }

    private void applyApprovedSnapshot(ActorSnapshot snapshot) {
        if (snapshot == null) {
            this.approvedByDisplayName = null;
            this.approvedByRoleLabel = null;
            this.approvedByEmployeeCode = null;
            this.approvedByUsername = null;
            return;
        }
        this.approvedByDisplayName = snapshot.displayName();
        this.approvedByRoleLabel = snapshot.roleLabel();
        this.approvedByEmployeeCode = snapshot.employeeCode();
        this.approvedByUsername = snapshot.username();
    }

    private void applyActivationSnapshot(ActorSnapshot snapshot) {
        if (snapshot == null) {
            this.activationByDisplayName = null;
            this.activationByRoleLabel = null;
            this.activationByEmployeeCode = null;
            this.activationByUsername = null;
            return;
        }
        this.activationByDisplayName = snapshot.displayName();
        this.activationByRoleLabel = snapshot.roleLabel();
        this.activationByEmployeeCode = snapshot.employeeCode();
        this.activationByUsername = snapshot.username();
    }

    public record ActorSnapshot(String displayName, String roleLabel, String employeeCode, String username) {}
}
