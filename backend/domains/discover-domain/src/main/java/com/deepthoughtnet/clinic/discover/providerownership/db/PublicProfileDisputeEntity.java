package com.deepthoughtnet.clinic.discover.providerownership.db;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileDisputeStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
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
@Table(name = "discover_public_profile_disputes")
public class PublicProfileDisputeEntity {
    @Id
    private UUID id;

    @Column(name = "public_profile_reference", nullable = false, length = 160)
    private String publicProfileReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_profile_type", nullable = false, length = 32)
    private PublicProfileType publicProfileType;

    @Column(name = "ownership_id")
    private UUID ownershipId;

    @Column(name = "claim_intent_reference", length = 120)
    private String claimIntentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false, length = 32)
    private PublicProfileDisputeStatus disputeStatus;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "resolution_reason", length = 1000)
    private String resolutionReason;

    @Column(name = "opened_by_app_user_id")
    private UUID openedByAppUserId;

    @Column(name = "resolved_by_app_user_id")
    private UUID resolvedByAppUserId;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PublicProfileDisputeEntity() {
    }

    public static PublicProfileDisputeEntity create(
            String publicProfileReference,
            PublicProfileType publicProfileType,
            UUID ownershipId,
            String claimIntentReference,
            UUID openedByAppUserId,
            String reason
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PublicProfileDisputeEntity entity = new PublicProfileDisputeEntity();
        entity.id = UUID.randomUUID();
        entity.publicProfileReference = publicProfileReference;
        entity.publicProfileType = publicProfileType;
        entity.ownershipId = ownershipId;
        entity.claimIntentReference = claimIntentReference;
        entity.disputeStatus = PublicProfileDisputeStatus.OPEN;
        entity.openedByAppUserId = openedByAppUserId;
        entity.reason = reason;
        entity.openedAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public PublicProfileType getPublicProfileType() { return publicProfileType; }
    public UUID getOwnershipId() { return ownershipId; }
    public String getClaimIntentReference() { return claimIntentReference; }
    public PublicProfileDisputeStatus getDisputeStatus() { return disputeStatus; }
    public String getReason() { return reason; }
    public String getResolutionReason() { return resolutionReason; }
    public UUID getOpenedByAppUserId() { return openedByAppUserId; }
    public UUID getResolvedByAppUserId() { return resolvedByAppUserId; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void requestEvidence(String reason) {
        this.disputeStatus = PublicProfileDisputeStatus.EVIDENCE_REQUESTED;
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void startReview(String reason) {
        this.disputeStatus = PublicProfileDisputeStatus.UNDER_REVIEW;
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void resolveForClaimant(UUID resolvedByAppUserId, String resolutionReason) {
        this.disputeStatus = PublicProfileDisputeStatus.RESOLVED_FOR_CLAIMANT;
        this.resolvedByAppUserId = resolvedByAppUserId;
        this.resolutionReason = resolutionReason;
        this.resolvedAt = OffsetDateTime.now();
        this.updatedAt = this.resolvedAt;
    }

    public void resolveForExistingOwner(UUID resolvedByAppUserId, String resolutionReason) {
        this.disputeStatus = PublicProfileDisputeStatus.RESOLVED_FOR_EXISTING_OWNER;
        this.resolvedByAppUserId = resolvedByAppUserId;
        this.resolutionReason = resolutionReason;
        this.resolvedAt = OffsetDateTime.now();
        this.updatedAt = this.resolvedAt;
    }

    public void revokeConnection(UUID resolvedByAppUserId, String resolutionReason) {
        this.disputeStatus = PublicProfileDisputeStatus.CONNECTION_REVOKED;
        this.resolvedByAppUserId = resolvedByAppUserId;
        this.resolutionReason = resolutionReason;
        this.resolvedAt = OffsetDateTime.now();
        this.updatedAt = this.resolvedAt;
    }

    public void close(UUID resolvedByAppUserId, String resolutionReason) {
        this.disputeStatus = PublicProfileDisputeStatus.CLOSED;
        this.resolvedByAppUserId = resolvedByAppUserId;
        this.resolutionReason = resolutionReason;
        this.resolvedAt = OffsetDateTime.now();
        this.updatedAt = this.resolvedAt;
    }
}
