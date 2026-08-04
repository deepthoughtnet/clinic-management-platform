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
            boolean current
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
            List<PublicProfileReviewFindingRecord> findings,
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
            int currentDraftVersion
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
