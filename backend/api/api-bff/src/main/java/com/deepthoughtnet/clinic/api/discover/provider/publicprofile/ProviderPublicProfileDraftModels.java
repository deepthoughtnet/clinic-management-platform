package com.deepthoughtnet.clinic.api.discover.provider.publicprofile;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProviderPublicProfileDraftModels {
    private ProviderPublicProfileDraftModels() {
    }

    public record ProviderPublicProfileDraftFieldSourceResponse(
            String sourceSystem,
            String sourceReference,
            long sourceRevision,
            OffsetDateTime importedAt,
            UUID lastEditedBy,
            OffsetDateTime lastEditedAt,
            boolean providerOverride
    ) {
    }

    public record ProviderPublicProfileDraftSectionResponse(
            String key,
            String title,
            Map<String, Object> content,
            Map<String, ProviderPublicProfileDraftFieldSourceResponse> sources
    ) {
    }

    public record ProviderPublicProfileDraftReadinessResponse(
            String readinessStatus,
            boolean ready,
            int completenessPercentage,
            List<String> missingMandatoryFields,
            List<String> recommendedFields,
            List<String> invalidFields,
            List<String> warnings,
            List<String> blockingReasons,
            OffsetDateTime lastEvaluatedAt,
            Integer evaluatedDraftVersion
    ) {
    }

    public record ProviderPublicProfileDraftVersionResponse(
            UUID id,
            int versionNumber,
            String changeSummary,
            OffsetDateTime createdAt,
            UUID createdByProviderAccountId
    ) {
    }

    public record ProviderPublicProfileDraftResponse(
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
            List<ProviderPublicProfileDraftSectionResponse> sections,
            ProviderPublicProfileDraftReadinessResponse readiness,
            List<ProviderPublicProfileDraftVersionResponse> versions,
            Map<String, ProviderPublicProfileDraftFieldSourceResponse> fieldSources
    ) {
    }

    public record ProviderPublicProfileDraftMediaUploadResponse(
            String mediaReference,
            ProviderPublicProfileDraftResponse draft
    ) {
    }

    public record ProviderPublicProfileDraftSectionUpdateRequest(
            String sectionKey,
            Map<String, Object> content,
            Long expectedVersion,
            String changeSummary
    ) {
    }
}
