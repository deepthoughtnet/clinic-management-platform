package com.deepthoughtnet.clinic.platform.providerintegration.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "provider_connection_suggestion_rejections")
public class ProviderConnectionSuggestionRejectionEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "suggestion_key", nullable = false, unique = true, length = 320)
    private String suggestionKey;

    @Column(name = "public_profile_type", nullable = false, length = 40)
    private String publicProfileType;

    @Column(name = "public_reference", length = 160)
    private String publicReference;

    @Column(name = "public_practice_reference", length = 160)
    private String publicPracticeReference;

    @Column(name = "tenant_reference", length = 160)
    private String tenantReference;

    @Column(name = "platform_clinic_reference", length = 160)
    private String platformClinicReference;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson;

    protected ProviderConnectionSuggestionRejectionEntity() {
    }

    public static ProviderConnectionSuggestionRejectionEntity create(
            UUID id,
            String suggestionKey,
            String publicProfileType,
            String publicReference,
            String publicPracticeReference,
            String tenantReference,
            String platformClinicReference,
            long sourceRevision,
            String reason,
            OffsetDateTime now,
            String metadataJson
    ) {
        ProviderConnectionSuggestionRejectionEntity entity = new ProviderConnectionSuggestionRejectionEntity();
        entity.id = id;
        entity.suggestionKey = suggestionKey;
        entity.publicProfileType = publicProfileType;
        entity.publicReference = publicReference;
        entity.publicPracticeReference = publicPracticeReference;
        entity.tenantReference = tenantReference;
        entity.platformClinicReference = platformClinicReference;
        entity.sourceRevision = sourceRevision;
        entity.reason = reason;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.metadataJson = metadataJson;
        entity.rowVersion = 0L;
        return entity;
    }

    public UUID getId() {
        return id;
    }

    public String getSuggestionKey() {
        return suggestionKey;
    }

    public String getPublicProfileType() {
        return publicProfileType;
    }

    public String getPublicReference() {
        return publicReference;
    }

    public String getPublicPracticeReference() {
        return publicPracticeReference;
    }

    public String getTenantReference() {
        return tenantReference;
    }

    public String getPlatformClinicReference() {
        return platformClinicReference;
    }

    public long getSourceRevision() {
        return sourceRevision;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getMetadataJson() {
        return metadataJson;
    }
}
