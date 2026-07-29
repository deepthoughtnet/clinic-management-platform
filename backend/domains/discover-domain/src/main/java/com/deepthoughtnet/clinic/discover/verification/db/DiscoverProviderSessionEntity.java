package com.deepthoughtnet.clinic.discover.verification.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_provider_sessions")
public class DiscoverProviderSessionEntity {
    @Id
    private UUID id;

    @Column(name = "provider_account_id", nullable = false)
    private UUID providerAccountId;

    @Column(name = "session_token_hash", nullable = false, length = 255)
    private String sessionTokenHash;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DiscoverProviderSessionEntity() {
    }

    public static DiscoverProviderSessionEntity create(UUID providerAccountId, String sessionTokenHash, OffsetDateTime issuedAt, OffsetDateTime expiresAt) {
        DiscoverProviderSessionEntity entity = new DiscoverProviderSessionEntity();
        entity.id = UUID.randomUUID();
        entity.providerAccountId = providerAccountId;
        entity.sessionTokenHash = sessionTokenHash;
        entity.issuedAt = issuedAt;
        entity.expiresAt = expiresAt;
        entity.createdAt = issuedAt;
        entity.updatedAt = issuedAt;
        entity.lastSeenAt = issuedAt;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProviderAccountId() {
        return providerAccountId;
    }

    public String getSessionTokenHash() {
        return sessionTokenHash;
    }

    public OffsetDateTime getIssuedAt() {
        return issuedAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        OffsetDateTime now = OffsetDateTime.now();
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
        this.updatedAt = this.revokedAt;
    }

    public void touch() {
        this.lastSeenAt = OffsetDateTime.now();
        this.updatedAt = this.lastSeenAt;
    }
}
