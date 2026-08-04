package com.deepthoughtnet.clinic.discover.publicprofiledraft;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PublicProfileDraftModels {
    private PublicProfileDraftModels() {
    }

    public record PublicProfileDraftFieldSourceRecord(
            String sourceSystem,
            String sourceReference,
            long sourceRevision,
            OffsetDateTime importedAt,
            UUID lastEditedBy,
            OffsetDateTime lastEditedAt,
            boolean providerOverride
    ) {
    }

    public record PublicProfileDraftSectionRecord(
            String key,
            String title,
            Map<String, Object> content,
            Map<String, PublicProfileDraftFieldSourceRecord> sources
    ) {
    }

    public record PublicProfileDraftReadinessRecord(
            String readinessStatus,
            boolean ready,
            int completenessPercentage,
            List<String> missingMandatoryFields,
            List<String> recommendedFields,
            List<String> invalidFields,
            List<String> warnings,
            List<String> blockingReasons,
            OffsetDateTime lastEvaluatedAt
    ) {
    }

    public record PublicProfileDraftVersionRecord(
            UUID id,
            int versionNumber,
            String changeSummary,
            OffsetDateTime createdAt,
            UUID createdByProviderAccountId
    ) {
    }

    public record PublicProfileDraftWorkspaceRecord(
            UUID draftId,
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
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime lastSavedAt,
            OffsetDateTime ownershipUpdatedAt,
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
            String sourceSystem,
            String sourceReference,
            long sourceRevision,
            OffsetDateTime sourceUpdatedAt,
            String publicProfilePath,
            List<String> allowedActions,
            List<PublicProfileDraftSectionRecord> sections,
            PublicProfileDraftReadinessRecord readiness,
            List<PublicProfileDraftVersionRecord> versions,
            Map<String, PublicProfileDraftFieldSourceRecord> fieldSources
    ) {
    }

    public record PublicProfileDraftMediaUploadRecord(
            String mediaReference,
            PublicProfileDraftWorkspaceRecord draft
    ) {
    }

    public record PublicProfileDraftMediaContentRecord(
            String mediaReference,
            String contentType,
            String originalFilename,
            byte[] bytes
    ) {
    }

    public record PublicProfileDraftSectionUpdateRequest(
            String sectionKey,
            Map<String, Object> content,
            Long expectedVersion,
            String changeSummary
    ) {
    }
}
