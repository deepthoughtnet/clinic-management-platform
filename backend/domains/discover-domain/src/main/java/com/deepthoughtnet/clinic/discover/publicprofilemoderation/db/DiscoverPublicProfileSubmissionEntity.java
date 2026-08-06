package com.deepthoughtnet.clinic.discover.publicprofilemoderation.db;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_public_profile_submissions")
public class DiscoverPublicProfileSubmissionEntity {
    @Id
    private UUID id;
    @Column(name = "submission_reference", nullable = false, unique = true, length = 120)
    private String submissionReference;
    @Column(name = "public_profile_reference", nullable = false, length = 160)
    private String publicProfileReference;
    @Enumerated(EnumType.STRING)
    @Column(name = "public_profile_type", nullable = false, length = 32)
    private ProviderType publicProfileType;
    @Column(name = "draft_reference", nullable = false, length = 120)
    private String draftReference;
    @Column(name = "submitted_draft_version", nullable = false)
    private int submittedDraftVersion;
    @Column(name = "moderation_status", nullable = false, length = 32)
    private String moderationStatus;
    @Column(name = "publication_status_snapshot", nullable = false, length = 32)
    private String publicationStatusSnapshot;
    @Column(name = "tenant_consent_status_snapshot", nullable = false, length = 32)
    private String tenantConsentStatusSnapshot;
    @Column(name = "ownership_snapshot_json", nullable = false, columnDefinition = "text")
    private String ownershipSnapshotJson;
    @Column(name = "readiness_snapshot_json", nullable = false, columnDefinition = "text")
    private String readinessSnapshotJson;
    @Column(name = "content_snapshot_json", nullable = false, columnDefinition = "text")
    private String contentSnapshotJson;
    @Column(name = "source_attribution_snapshot_json", nullable = false, columnDefinition = "text")
    private String sourceAttributionSnapshotJson;
    @Column(name = "media_snapshot_json", nullable = false, columnDefinition = "text")
    private String mediaSnapshotJson;
    @Column(name = "submitted_by_provider_account_id")
    private UUID submittedByProviderAccountId;
    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;
    @Column(name = "assigned_reviewer_id")
    private UUID assignedReviewerId;
    @Column(name = "assigned_reviewer_reference", length = 256)
    private String assignedReviewerReference;
    @Column(name = "assigned_reviewer_display_name", length = 256)
    private String assignedReviewerDisplayName;
    @Column(name = "assigned_reviewer_email", length = 256)
    private String assignedReviewerEmail;
    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;
    @Column(name = "decision_by_id")
    private UUID decisionById;
    @Column(name = "decision_at")
    private OffsetDateTime decisionAt;
    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;
    @Column(name = "moderation_revision", nullable = false)
    private long moderationRevision;
    @Column(name = "current_flag", nullable = false)
    private boolean current;
    @Column(name = "approved_version_number")
    private Integer approvedVersionNumber;
    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
    @Column(name = "unpublished_at")
    private OffsetDateTime unpublishedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version
    @Column(name = "optimistic_lock_version", nullable = false)
    private long optimisticLockVersion;

    protected DiscoverPublicProfileSubmissionEntity() {
    }

    public static DiscoverPublicProfileSubmissionEntity create(
            UUID id,
            String submissionReference,
            String publicProfileReference,
            ProviderType publicProfileType,
            String draftReference,
            int submittedDraftVersion,
            String moderationStatus,
            String publicationStatusSnapshot,
            String tenantConsentStatusSnapshot,
            String ownershipSnapshotJson,
            String readinessSnapshotJson,
            String contentSnapshotJson,
            String sourceAttributionSnapshotJson,
            String mediaSnapshotJson,
            UUID submittedByProviderAccountId,
            OffsetDateTime submittedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        DiscoverPublicProfileSubmissionEntity entity = new DiscoverPublicProfileSubmissionEntity();
        entity.id = id;
        entity.submissionReference = submissionReference;
        entity.publicProfileReference = publicProfileReference;
        entity.publicProfileType = publicProfileType;
        entity.draftReference = draftReference;
        entity.submittedDraftVersion = submittedDraftVersion;
        entity.moderationStatus = moderationStatus;
        entity.publicationStatusSnapshot = publicationStatusSnapshot;
        entity.tenantConsentStatusSnapshot = tenantConsentStatusSnapshot;
        entity.ownershipSnapshotJson = ownershipSnapshotJson;
        entity.readinessSnapshotJson = readinessSnapshotJson;
        entity.contentSnapshotJson = contentSnapshotJson;
        entity.sourceAttributionSnapshotJson = sourceAttributionSnapshotJson;
        entity.mediaSnapshotJson = mediaSnapshotJson;
        entity.submittedByProviderAccountId = submittedByProviderAccountId;
        entity.submittedAt = submittedAt;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.current = true;
        entity.moderationRevision = 0L;
        return entity;
    }

    public void markCurrent(boolean current, OffsetDateTime updatedAt) {
        this.current = current;
        this.updatedAt = updatedAt;
    }

    public void markSubmitted(UUID submittedByProviderAccountId, OffsetDateTime submittedAt, OffsetDateTime updatedAt) {
        this.submittedByProviderAccountId = submittedByProviderAccountId;
        this.submittedAt = submittedAt;
        this.updatedAt = updatedAt;
        this.moderationStatus = "SUBMITTED";
    }

    public void startReview(UUID reviewerId, OffsetDateTime assignedAt, OffsetDateTime updatedAt) {
        startReview(reviewerId, null, null, null, assignedAt, updatedAt);
    }

    public void startReview(UUID reviewerId, String reviewerReference, String reviewerDisplayName, String reviewerEmail, OffsetDateTime assignedAt, OffsetDateTime updatedAt) {
        this.assignedReviewerId = reviewerId;
        this.assignedReviewerReference = reviewerReference;
        this.assignedReviewerDisplayName = reviewerDisplayName;
        this.assignedReviewerEmail = reviewerEmail;
        this.assignedAt = assignedAt;
        this.updatedAt = updatedAt;
        this.moderationStatus = "UNDER_REVIEW";
        this.moderationRevision++;
    }

    public void requestChanges(UUID decisionById, OffsetDateTime decisionAt, String reason, OffsetDateTime updatedAt) {
        this.decisionById = decisionById;
        this.decisionAt = decisionAt;
        this.decisionReason = reason;
        this.updatedAt = updatedAt;
        this.moderationStatus = "CHANGES_REQUESTED";
        this.current = false;
        this.moderationRevision++;
    }

    public void approve(UUID decisionById, OffsetDateTime decisionAt, String reason, Integer approvedVersionNumber, OffsetDateTime updatedAt) {
        this.decisionById = decisionById;
        this.decisionAt = decisionAt;
        this.decisionReason = reason;
        this.approvedVersionNumber = approvedVersionNumber;
        this.updatedAt = updatedAt;
        this.moderationStatus = "APPROVED";
        this.current = false;
        this.moderationRevision++;
    }

    public void reject(UUID decisionById, OffsetDateTime decisionAt, String reason, OffsetDateTime updatedAt) {
        this.decisionById = decisionById;
        this.decisionAt = decisionAt;
        this.decisionReason = reason;
        this.updatedAt = updatedAt;
        this.moderationStatus = "REJECTED";
        this.current = false;
        this.moderationRevision++;
    }

    public void withdraw(UUID decisionById, OffsetDateTime decisionAt, String reason, OffsetDateTime updatedAt) {
        this.decisionById = decisionById;
        this.decisionAt = decisionAt;
        this.decisionReason = reason;
        this.updatedAt = updatedAt;
        this.moderationStatus = "WITHDRAWN";
        this.current = false;
        this.moderationRevision++;
    }

    public void touchReview(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
        this.moderationRevision++;
    }

    public void markPublished(OffsetDateTime publishedAt, OffsetDateTime updatedAt) {
        this.publicationStatusSnapshot = "PUBLISHED";
        this.publishedAt = publishedAt;
        this.unpublishedAt = null;
        this.updatedAt = updatedAt;
    }

    public void markUnpublished(OffsetDateTime unpublishedAt, OffsetDateTime updatedAt) {
        this.publicationStatusSnapshot = "UNPUBLISHED";
        this.unpublishedAt = unpublishedAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getSubmissionReference() { return submissionReference; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public ProviderType getPublicProfileType() { return publicProfileType; }
    public String getDraftReference() { return draftReference; }
    public int getSubmittedDraftVersion() { return submittedDraftVersion; }
    public String getModerationStatus() { return moderationStatus; }
    public String getPublicationStatusSnapshot() { return publicationStatusSnapshot; }
    public String getTenantConsentStatusSnapshot() { return tenantConsentStatusSnapshot; }
    public String getOwnershipSnapshotJson() { return ownershipSnapshotJson; }
    public String getReadinessSnapshotJson() { return readinessSnapshotJson; }
    public String getContentSnapshotJson() { return contentSnapshotJson; }
    public String getSourceAttributionSnapshotJson() { return sourceAttributionSnapshotJson; }
    public String getMediaSnapshotJson() { return mediaSnapshotJson; }
    public UUID getSubmittedByProviderAccountId() { return submittedByProviderAccountId; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public UUID getAssignedReviewerId() { return assignedReviewerId; }
    public String getAssignedReviewerReference() { return assignedReviewerReference; }
    public String getAssignedReviewerDisplayName() { return assignedReviewerDisplayName; }
    public String getAssignedReviewerEmail() { return assignedReviewerEmail; }
    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public UUID getDecisionById() { return decisionById; }
    public OffsetDateTime getDecisionAt() { return decisionAt; }
    public String getDecisionReason() { return decisionReason; }
    public long getModerationRevision() { return moderationRevision; }
    public boolean isCurrent() { return current; }
    public Integer getApprovedVersionNumber() { return approvedVersionNumber; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getUnpublishedAt() { return unpublishedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
