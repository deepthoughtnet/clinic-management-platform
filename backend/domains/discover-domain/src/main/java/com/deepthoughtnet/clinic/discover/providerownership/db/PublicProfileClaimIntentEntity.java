package com.deepthoughtnet.clinic.discover.providerownership.db;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipJsonSupport;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus;
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
@Table(name = "discover_provider_claim_intents")
public class PublicProfileClaimIntentEntity {
    @Id
    private UUID id;

    @Column(name = "connection_reference", nullable = false, unique = true, length = 120)
    private String connectionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_profile_type", nullable = false, length = 32)
    private PublicProfileType publicProfileType;

    @Column(name = "public_profile_reference", nullable = false, length = 160)
    private String publicProfileReference;

    @Column(name = "tenant_reference", nullable = false, length = 160)
    private String tenantReference;

    @Column(name = "provider_account_id")
    private UUID providerAccountId;

    @Column(name = "issuer_app_user_id")
    private UUID issuerAppUserId;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PublicProfileClaimIntentStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "provider_authenticated_at")
    private OffsetDateTime providerAuthenticatedAt;

    @Column(name = "claim_submitted_at")
    private OffsetDateTime claimSubmittedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "reason", length = 512)
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

    protected PublicProfileClaimIntentEntity() {
    }

    public static PublicProfileClaimIntentEntity create(
            String connectionReference,
            PublicProfileType publicProfileType,
            String publicProfileReference,
            String tenantReference,
            UUID issuerAppUserId,
            long sourceRevision,
            OffsetDateTime expiresAt,
            String reason
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        PublicProfileClaimIntentEntity entity = new PublicProfileClaimIntentEntity();
        entity.id = UUID.randomUUID();
        entity.connectionReference = connectionReference;
        entity.publicProfileType = publicProfileType;
        entity.publicProfileReference = publicProfileReference;
        entity.tenantReference = tenantReference;
        entity.issuerAppUserId = issuerAppUserId;
        entity.sourceRevision = sourceRevision;
        entity.status = PublicProfileClaimIntentStatus.CREATED;
        entity.expiresAt = expiresAt;
        entity.reason = reason;
        entity.evidenceSnapshotJson = ProviderOwnershipJsonSupport.parseEvidenceSnapshot(null);
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public String getConnectionReference() { return connectionReference; }
    public PublicProfileType getPublicProfileType() { return publicProfileType; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public String getTenantReference() { return tenantReference; }
    public UUID getProviderAccountId() { return providerAccountId; }
    public UUID getIssuerAppUserId() { return issuerAppUserId; }
    public long getSourceRevision() { return sourceRevision; }
    public PublicProfileClaimIntentStatus getStatus() { return status; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getOpenedAt() { return openedAt; }
    public OffsetDateTime getProviderAuthenticatedAt() { return providerAuthenticatedAt; }
    public OffsetDateTime getClaimSubmittedAt() { return claimSubmittedAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; }
    public String getReason() { return reason; }
    public String getEvidenceSnapshotJson() { return ProviderOwnershipJsonSupport.writeJson(evidenceSnapshotJson); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void open() {
        this.status = PublicProfileClaimIntentStatus.OPENED;
        this.openedAt = OffsetDateTime.now();
        this.updatedAt = this.openedAt;
    }

    public void authenticate(UUID providerAccountId) {
        this.providerAccountId = providerAccountId;
        this.status = PublicProfileClaimIntentStatus.PROVIDER_AUTHENTICATED;
        this.providerAuthenticatedAt = OffsetDateTime.now();
        this.updatedAt = this.providerAuthenticatedAt;
    }

    public void submit(UUID providerAccountId, String evidenceSnapshotJson) {
        this.providerAccountId = providerAccountId;
        this.status = PublicProfileClaimIntentStatus.CLAIM_SUBMITTED;
        this.evidenceSnapshotJson = ProviderOwnershipJsonSupport.parseEvidenceSnapshot(evidenceSnapshotJson);
        this.claimSubmittedAt = OffsetDateTime.now();
        this.consumedAt = this.claimSubmittedAt;
        this.updatedAt = this.claimSubmittedAt;
    }

    public void reject(String reason) {
        this.status = PublicProfileClaimIntentStatus.REJECTED;
        this.reason = reason;
        this.rejectedAt = OffsetDateTime.now();
        this.updatedAt = this.rejectedAt;
    }

    public void revoke(String reason) {
        this.status = PublicProfileClaimIntentStatus.REVOKED;
        this.reason = reason;
        this.revokedAt = OffsetDateTime.now();
        this.updatedAt = this.revokedAt;
    }
}
