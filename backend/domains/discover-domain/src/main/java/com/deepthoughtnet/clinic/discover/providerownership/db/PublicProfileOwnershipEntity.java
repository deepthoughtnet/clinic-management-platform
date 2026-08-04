package com.deepthoughtnet.clinic.discover.providerownership.db;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipJsonSupport;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "discover_public_profile_ownerships")
public class PublicProfileOwnershipEntity {
    @Id
    private UUID id;

    @Column(name = "public_profile_reference", nullable = false, length = 160)
    private String publicProfileReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_profile_type", nullable = false, length = 32)
    private PublicProfileType publicProfileType;

    @Column(name = "provider_account_id", nullable = false)
    private UUID providerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_status", nullable = false, length = 32)
    private PublicProfileOwnershipStatus status;

    @Column(name = "ownership_method", nullable = false, length = 64)
    private String ownershipMethod;

    @Column(name = "tenant_reference", length = 160)
    private String tenantReference;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "transfer_target_provider_account_id")
    private UUID transferTargetProviderAccountId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "reason", length = 1000)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_snapshot_json", columnDefinition = "jsonb")
    private JsonNode evidenceSnapshotJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PublicProfileOwnershipEntity() {
    }

    public static PublicProfileOwnershipEntity create(
            String publicProfileReference,
            PublicProfileType publicProfileType,
            UUID providerAccountId,
            String ownershipMethod,
            String tenantReference,
            long sourceRevision,
            String reason
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PublicProfileOwnershipEntity entity = new PublicProfileOwnershipEntity();
        entity.id = UUID.randomUUID();
        entity.publicProfileReference = publicProfileReference;
        entity.publicProfileType = publicProfileType;
        entity.providerAccountId = providerAccountId;
        entity.status = PublicProfileOwnershipStatus.CLAIM_PENDING;
        entity.ownershipMethod = ownershipMethod;
        entity.tenantReference = tenantReference;
        entity.sourceRevision = sourceRevision;
        entity.active = false;
        entity.reason = reason;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public PublicProfileType getPublicProfileType() { return publicProfileType; }
    public UUID getProviderAccountId() { return providerAccountId; }
    public PublicProfileOwnershipStatus getStatus() { return status; }
    public String getOwnershipMethod() { return ownershipMethod; }
    public String getTenantReference() { return tenantReference; }
    public long getSourceRevision() { return sourceRevision; }
    public OffsetDateTime getVerifiedAt() { return verifiedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getTransferTargetProviderAccountId() { return transferTargetProviderAccountId; }
    public boolean isActive() { return active; }
    public String getReason() { return reason; }
    public String getEvidenceSnapshotJson() { return ProviderOwnershipJsonSupport.writeJson(evidenceSnapshotJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }

    public void markVerified(String reason) {
        this.status = PublicProfileOwnershipStatus.VERIFIED;
        this.active = true;
        this.reason = reason;
        this.verifiedAt = OffsetDateTime.now();
        this.updatedAt = this.verifiedAt;
    }

    public void markClaimPending(String reason) {
        this.status = PublicProfileOwnershipStatus.CLAIM_PENDING;
        this.active = false;
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void recordEvidenceSnapshot(String evidenceSnapshotJson) {
        this.evidenceSnapshotJson = ProviderOwnershipJsonSupport.parseEvidenceSnapshot(evidenceSnapshotJson);
        this.updatedAt = OffsetDateTime.now();
    }

    public void markRejected(String reason) {
        this.status = PublicProfileOwnershipStatus.REJECTED;
        this.active = false;
        this.rejectionReason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markDisputed(String reason) {
        this.status = PublicProfileOwnershipStatus.DISPUTED;
        this.active = false;
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void markRevoked(String reason) {
        this.status = PublicProfileOwnershipStatus.REVOKED;
        this.active = false;
        this.rejectionReason = reason;
        this.revokedAt = OffsetDateTime.now();
        this.updatedAt = this.revokedAt;
    }

    public void markTransferPending(UUID targetProviderAccountId, String reason) {
        this.status = PublicProfileOwnershipStatus.TRANSFER_PENDING;
        this.transferTargetProviderAccountId = targetProviderAccountId;
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }
}
