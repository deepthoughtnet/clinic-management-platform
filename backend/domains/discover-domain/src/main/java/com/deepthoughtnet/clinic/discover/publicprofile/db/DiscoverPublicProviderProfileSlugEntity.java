package com.deepthoughtnet.clinic.discover.publicprofile.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discover_public_provider_profile_slugs")
public class DiscoverPublicProviderProfileSlugEntity {
    @Id
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private UUID providerId;

    @Column(name = "profile_version_id", nullable = false)
    private UUID profileVersionId;

    @Column(name = "slug", nullable = false, unique = true, length = 256)
    private String slug;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DiscoverPublicProviderProfileSlugEntity() {
    }

    public static DiscoverPublicProviderProfileSlugEntity create(UUID providerId, UUID profileVersionId, String slug, int versionNumber, boolean active, OffsetDateTime now) {
        DiscoverPublicProviderProfileSlugEntity entity = new DiscoverPublicProviderProfileSlugEntity();
        entity.id = UUID.randomUUID();
        entity.providerId = providerId;
        entity.profileVersionId = profileVersionId;
        entity.slug = slug;
        entity.versionNumber = versionNumber;
        entity.active = active;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.rowVersion = 0L;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getProviderId() { return providerId; }
    public UUID getProfileVersionId() { return profileVersionId; }
    public String getSlug() { return slug; }
    public int getVersionNumber() { return versionNumber; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getRowVersion() { return rowVersion; }

    public void activate(UUID profileVersionId, int versionNumber, OffsetDateTime now) {
        this.profileVersionId = profileVersionId;
        this.versionNumber = versionNumber;
        this.active = true;
        this.updatedAt = now;
    }

    public void deactivate(OffsetDateTime now) {
        this.active = false;
        this.updatedAt = now;
    }
}
