package com.deepthoughtnet.clinic.discover.publicprofilemoderation;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PublicProfileModerationModels {
    private PublicProfileModerationModels() {
    }

    public record PublicProfileReviewFindingRecord(
            UUID id,
            String findingReference,
            String submissionReference,
            String section,
            String fieldKey,
            String category,
            String severity,
            boolean required,
            String reviewerNote,
            String providerFacingMessage,
            String internalNote,
            String resolutionStatus,
            String providerResolutionNote,
            OffsetDateTime createdAt,
            OffsetDateTime resolvedAt
    ) {
    }

    public record PublicProfilePublicationRecord(
            UUID id,
            String publicationReference,
            String publicProfileReference,
            String approvedSubmissionReference,
            int publishedVersion,
            String publicationStatus,
            String slug,
            String publicPath,
            String reason,
            OffsetDateTime publishedAt,
            OffsetDateTime unpublishedAt,
            boolean current,
            String effectiveVisibility,
            String visibilityReason
    ) {
    }

    public record PublicProfileModerationSubmissionRecord(
            UUID id,
            String submissionReference,
            String publicProfileReference,
            ProviderType publicProfileType,
            String draftReference,
            int submittedDraftVersion,
            String moderationStatus,
            String publicationStatusSnapshot,
            String tenantConsentStatusSnapshot,
            Map<String, Object> ownershipSnapshot,
            Map<String, Object> readinessSnapshot,
            Map<String, Object> contentSnapshot,
            Map<String, Object> sourceAttributionSnapshot,
            Map<String, Object> mediaSnapshot,
            UUID submittedByProviderAccountId,
            OffsetDateTime submittedAt,
            UUID assignedReviewerId,
            String assignedReviewerReference,
            String assignedReviewerDisplayName,
            String assignedReviewerEmail,
            OffsetDateTime assignedAt,
            UUID decisionById,
            OffsetDateTime decisionAt,
            String decisionReason,
            long moderationRevision,
            boolean current,
            Integer approvedVersionNumber,
            OffsetDateTime publishedAt,
            OffsetDateTime unpublishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String effectiveVisibility,
            String visibilityReason,
            String publicUrl,
            List<PublicProfileReviewFindingRecord> findings,
            List<String> providerAllowedActions,
            List<String> allowedActions
    ) {
    }

    public record PublicProfileModerationQueueRecord(
            String publicProfileReference,
            ProviderType publicProfileType,
            String displayName,
            String city,
            String area,
            String ownershipStatus,
            String tenantConsentStatus,
            String contentStatus,
            String readinessStatus,
            int completenessPercentage,
            String moderationStatus,
            String publicationStatus,
            String submissionReference,
            Integer submittedDraftVersion,
            OffsetDateTime submittedAt,
            String assignedReviewer,
            OffsetDateTime assignedAt,
            long ageInQueueDays,
            String sourceType,
            String effectiveVisibility,
            String visibilityReason,
            String publicUrl,
            List<String> allowedActions
    ) {
    }

    public record PublicProfileSubmissionEligibilityRecord(
            boolean submissionEligible,
            List<String> submissionBlockers,
            List<String> allowedActions,
            String moderationStatus,
            String publicationStatus,
            String submissionReference,
            Integer submittedDraftVersion,
            OffsetDateTime submittedAt,
            OffsetDateTime reviewedAt,
            int currentDraftVersion,
            boolean editable,
            String publicUrl
    ) {
    }

    public record PublicProfileModerationDecisionRecord(
            String submissionReference,
            String moderationStatus,
            String publicationStatus,
            OffsetDateTime decisionAt,
            UUID decisionById,
            String decisionReason,
            long moderationRevision,
            boolean current
    ) {
    }
}
