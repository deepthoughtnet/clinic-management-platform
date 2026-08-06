package com.deepthoughtnet.clinic.discover.publicprofiledraft.db;

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
@Table(name = "discover_public_profile_drafts")
public class DiscoverPublicProfileDraftEntity {
    @Id
    private UUID id;

    @Column(name = "draft_reference", nullable = false, unique = true, length = 120)
    private String draftReference;

    @Column(name = "public_profile_reference", nullable = false, unique = true, length = 160)
    private String publicProfileReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_profile_type", nullable = false, length = 32)
    private ProviderType publicProfileType;

    @Column(name = "provider_account_id", nullable = false)
    private UUID providerAccountId;

    @Column(name = "content_status", nullable = false, length = 32)
    private String contentStatus;

    @Column(name = "readiness_status", nullable = false, length = 32)
    private String readinessStatus;

    @Column(name = "completeness_percentage", nullable = false)
    private int completenessPercentage;

    @Column(name = "ownership_status", nullable = false, length = 32)
    private String ownershipStatus;

    @Column(name = "tenant_consent_status", nullable = false, length = 32)
    private String tenantConsentStatus;

    @Column(name = "public_profile_status", nullable = false, length = 32)
    private String publicProfileStatus;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "source_system", length = 64)
    private String sourceSystem;

    @Column(name = "source_reference", length = 160)
    private String sourceReference;

    @Column(name = "source_revision", nullable = false)
    private long sourceRevision;

    @Column(name = "source_updated_at")
    private OffsetDateTime sourceUpdatedAt;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "canonical_slug", length = 256)
    private String canonicalSlug;

    @Column(length = 128)
    private String city;

    @Column(length = 128)
    private String area;

    @Column(length = 128)
    private String state;

    @Column(length = 128)
    private String country;

    @Column(name = "public_phone", length = 64)
    private String publicPhone;

    @Column(name = "public_email", length = 256)
    private String publicEmail;

    @Column(length = 256)
    private String website;

    @Column(name = "whatsapp_number", length = 64)
    private String whatsappNumber;

    @Column(name = "registration_number", length = 128)
    private String registrationNumber;

    @Column(name = "established_year")
    private Integer establishedYear;

    @Column(name = "last_saved_at")
    private OffsetDateTime lastSavedAt;

    @Column(name = "created_by_provider_account_id")
    private UUID createdByProviderAccountId;

    @Column(name = "updated_by_provider_account_id")
    private UUID updatedByProviderAccountId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "optimistic_lock_version", nullable = false)
    private long optimisticLockVersion;

    @Column(name = "public_path", length = 256)
    private String publicPath;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    private String contentJson;

    @Column(name = "source_attribution_json", nullable = false, columnDefinition = "text")
    private String sourceAttributionJson;

    @Column(name = "readiness_json", nullable = false, columnDefinition = "text")
    private String readinessJson;

    protected DiscoverPublicProfileDraftEntity() {
    }

    public static DiscoverPublicProfileDraftEntity create(
            UUID id,
            String draftReference,
            String publicProfileReference,
            ProviderType publicProfileType,
            UUID providerAccountId,
            String ownershipStatus,
            String tenantConsentStatus,
            String publicProfileStatus,
            String contentStatus,
            String readinessStatus,
            int completenessPercentage,
            int currentVersion,
            String sourceSystem,
            String sourceReference,
            long sourceRevision,
            OffsetDateTime sourceUpdatedAt,
            String displayName,
            String canonicalSlug,
            String city,
            String area,
            String state,
            String country,
            String publicPhone,
            String publicEmail,
            String website,
            String whatsappNumber,
            String registrationNumber,
            Integer establishedYear,
            OffsetDateTime lastSavedAt,
            UUID createdByProviderAccountId,
            UUID updatedByProviderAccountId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String publicPath,
            String contentJson,
            String sourceAttributionJson,
            String readinessJson
    ) {
        DiscoverPublicProfileDraftEntity entity = new DiscoverPublicProfileDraftEntity();
        entity.id = id;
        entity.draftReference = draftReference;
        entity.publicProfileReference = publicProfileReference;
        entity.publicProfileType = publicProfileType;
        entity.providerAccountId = providerAccountId;
        entity.ownershipStatus = ownershipStatus;
        entity.tenantConsentStatus = tenantConsentStatus;
        entity.publicProfileStatus = publicProfileStatus;
        entity.contentStatus = contentStatus;
        entity.readinessStatus = readinessStatus;
        entity.completenessPercentage = completenessPercentage;
        entity.currentVersion = currentVersion;
        entity.sourceSystem = sourceSystem;
        entity.sourceReference = sourceReference;
        entity.sourceRevision = sourceRevision;
        entity.sourceUpdatedAt = sourceUpdatedAt;
        entity.displayName = displayName;
        entity.canonicalSlug = canonicalSlug;
        entity.city = city;
        entity.area = area;
        entity.state = state;
        entity.country = country;
        entity.publicPhone = publicPhone;
        entity.publicEmail = publicEmail;
        entity.website = website;
        entity.whatsappNumber = whatsappNumber;
        entity.registrationNumber = registrationNumber;
        entity.establishedYear = establishedYear;
        entity.lastSavedAt = lastSavedAt;
        entity.createdByProviderAccountId = createdByProviderAccountId;
        entity.updatedByProviderAccountId = updatedByProviderAccountId;
        entity.createdAt = createdAt;
        entity.updatedAt = updatedAt;
        entity.publicPath = publicPath;
        entity.contentJson = contentJson;
        entity.sourceAttributionJson = sourceAttributionJson;
        entity.readinessJson = readinessJson;
        entity.optimisticLockVersion = 0L;
        return entity;
    }

    public void update(
            String contentStatus,
            String readinessStatus,
            int completenessPercentage,
            String tenantConsentStatus,
            String publicProfileStatus,
            int currentVersion,
            String displayName,
            String canonicalSlug,
            String city,
            String area,
            String state,
            String country,
            String publicPhone,
            String publicEmail,
            String website,
            String whatsappNumber,
            String registrationNumber,
            Integer establishedYear,
            OffsetDateTime lastSavedAt,
            UUID updatedByProviderAccountId,
            String publicPath,
            String contentJson,
            String sourceAttributionJson,
            String readinessJson
    ) {
        this.contentStatus = contentStatus;
        this.readinessStatus = readinessStatus;
        this.completenessPercentage = completenessPercentage;
        this.tenantConsentStatus = tenantConsentStatus;
        this.publicProfileStatus = publicProfileStatus;
        this.currentVersion = currentVersion;
        this.displayName = displayName;
        this.canonicalSlug = canonicalSlug;
        this.city = city;
        this.area = area;
        this.state = state;
        this.country = country;
        this.publicPhone = publicPhone;
        this.publicEmail = publicEmail;
        this.website = website;
        this.whatsappNumber = whatsappNumber;
        this.registrationNumber = registrationNumber;
        this.establishedYear = establishedYear;
        this.lastSavedAt = lastSavedAt;
        this.updatedByProviderAccountId = updatedByProviderAccountId;
        this.updatedAt = lastSavedAt;
        this.publicPath = publicPath;
        this.contentJson = contentJson;
        this.sourceAttributionJson = sourceAttributionJson;
        this.readinessJson = readinessJson;
    }

    public UUID getId() { return id; }
    public String getDraftReference() { return draftReference; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public ProviderType getPublicProfileType() { return publicProfileType; }
    public UUID getProviderAccountId() { return providerAccountId; }
    public String getContentStatus() { return contentStatus; }
    public String getReadinessStatus() { return readinessStatus; }
    public int getCompletenessPercentage() { return completenessPercentage; }
    public String getOwnershipStatus() { return ownershipStatus; }
    public String getTenantConsentStatus() { return tenantConsentStatus; }
    public String getPublicProfileStatus() { return publicProfileStatus; }
    public int getCurrentVersion() { return currentVersion; }
    public String getSourceSystem() { return sourceSystem; }
    public String getSourceReference() { return sourceReference; }
    public long getSourceRevision() { return sourceRevision; }
    public OffsetDateTime getSourceUpdatedAt() { return sourceUpdatedAt; }
    public String getDisplayName() { return displayName; }
    public String getCanonicalSlug() { return canonicalSlug; }
    public String getCity() { return city; }
    public String getArea() { return area; }
    public String getState() { return state; }
    public String getCountry() { return country; }
    public String getPublicPhone() { return publicPhone; }
    public String getPublicEmail() { return publicEmail; }
    public String getWebsite() { return website; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public String getRegistrationNumber() { return registrationNumber; }
    public Integer getEstablishedYear() { return establishedYear; }
    public OffsetDateTime getLastSavedAt() { return lastSavedAt; }
    public UUID getCreatedByProviderAccountId() { return createdByProviderAccountId; }
    public UUID getUpdatedByProviderAccountId() { return updatedByProviderAccountId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public long getOptimisticLockVersion() { return optimisticLockVersion; }
    public String getPublicPath() { return publicPath; }
    public String getContentJson() { return contentJson; }
    public String getSourceAttributionJson() { return sourceAttributionJson; }
    public String getReadinessJson() { return readinessJson; }
}
