package com.deepthoughtnet.clinic.discover.providerownership.db;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole;
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
@Table(name = "discover_public_profile_memberships")
public class PublicProfileMembershipEntity {
    @Id
    private UUID id;

    @Column(name = "public_profile_reference", nullable = false, length = 160)
    private String publicProfileReference;

    @Column(name = "provider_account_id", nullable = false)
    private UUID providerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_role", nullable = false, length = 32)
    private PublicProfileMembershipRole role;

    @Column(name = "membership_status", nullable = false, length = 32)
    private String status;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected PublicProfileMembershipEntity() {
    }

    public static PublicProfileMembershipEntity create(String publicProfileReference, UUID providerAccountId, PublicProfileMembershipRole role, String reason, long sourceRevision) {
        OffsetDateTime now = OffsetDateTime.now();
        PublicProfileMembershipEntity entity = new PublicProfileMembershipEntity();
        entity.id = UUID.randomUUID();
        entity.publicProfileReference = publicProfileReference;
        entity.providerAccountId = providerAccountId;
        entity.role = role;
        entity.status = "PENDING";
        entity.sourceRevision = sourceRevision;
        entity.reason = reason;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public UUID getProviderAccountId() { return providerAccountId; }
    public PublicProfileMembershipRole getRole() { return role; }
    public String getStatus() { return status; }
    public long getSourceRevision() { return sourceRevision; }
    public String getReason() { return reason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void activate(String reason) {
        this.status = "ACTIVE";
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }

    public void deactivate(String reason) {
        this.status = "INACTIVE";
        this.reason = reason;
        this.updatedAt = OffsetDateTime.now();
    }
}
