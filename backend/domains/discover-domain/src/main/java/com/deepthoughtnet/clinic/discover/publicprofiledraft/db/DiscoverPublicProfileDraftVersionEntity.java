package com.deepthoughtnet.clinic.discover.publicprofiledraft.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "discover_public_profile_draft_versions")
public class DiscoverPublicProfileDraftVersionEntity {
    @Id
    private UUID id;

    @Column(name = "draft_reference", nullable = false, length = 120)
    private String draftReference;

    @Column(name = "public_profile_reference", nullable = false, length = 160)
    private String publicProfileReference;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    private String contentJson;

    @Column(name = "readiness_json", nullable = false, columnDefinition = "text")
    private String readinessJson;

    @Column(name = "source_attribution_json", nullable = false, columnDefinition = "text")
    private String sourceAttributionJson;

    @Column(name = "created_by_provider_account_id")
    private UUID createdByProviderAccountId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DiscoverPublicProfileDraftVersionEntity() {
    }

    public static DiscoverPublicProfileDraftVersionEntity create(
            String draftReference,
            String publicProfileReference,
            int versionNumber,
            String changeSummary,
            String contentJson,
            String readinessJson,
            String sourceAttributionJson,
            UUID createdByProviderAccountId,
            OffsetDateTime createdAt
    ) {
        DiscoverPublicProfileDraftVersionEntity entity = new DiscoverPublicProfileDraftVersionEntity();
        entity.id = UUID.randomUUID();
        entity.draftReference = draftReference;
        entity.publicProfileReference = publicProfileReference;
        entity.versionNumber = versionNumber;
        entity.changeSummary = changeSummary;
        entity.contentJson = contentJson;
        entity.readinessJson = readinessJson;
        entity.sourceAttributionJson = sourceAttributionJson;
        entity.createdByProviderAccountId = createdByProviderAccountId;
        entity.createdAt = createdAt;
        return entity;
    }

    public UUID getId() { return id; }
    public String getDraftReference() { return draftReference; }
    public String getPublicProfileReference() { return publicProfileReference; }
    public int getVersionNumber() { return versionNumber; }
    public String getChangeSummary() { return changeSummary; }
    public String getContentJson() { return contentJson; }
    public String getReadinessJson() { return readinessJson; }
    public String getSourceAttributionJson() { return sourceAttributionJson; }
    public UUID getCreatedByProviderAccountId() { return createdByProviderAccountId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
