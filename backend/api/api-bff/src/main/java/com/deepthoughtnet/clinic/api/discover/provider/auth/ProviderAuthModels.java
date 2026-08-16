package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.verification.VerificationChannel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;

public final class ProviderAuthModels {
    private ProviderAuthModels() {
    }

    public record LoginRequest(@NotBlank String identifier) {
    }

    public record LoginChallengeResponse(
            String challengeId,
            VerificationChannel channel,
            String maskedRecipient,
            String message,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String developmentCode,
            String verificationMode,
            OffsetDateTime expiresAt,
            OffsetDateTime resendAvailableAt,
            long expiresInSeconds,
            long resendAfterSeconds,
            String providerName,
            String deliveryReference
    ) {
    }

    public record LoginVerifyRequest(@NotBlank String identifier, @NotBlank String code) {
    }

    public record ChallengeVerifyRequest(@NotBlank String code) {
    }

    public record LoginVerifyResponse(
            boolean verified,
            OffsetDateTime sessionExpiresAt,
            String message
    ) {
    }

    public record WorkspaceApplicationResponse(
            UUID id,
            String referenceNumber,
            ProviderType providerType,
            ProviderLifecycleStatus status,
            String displayName,
            int completionPercent,
            String currentStep,
            boolean contactVerified,
            boolean requiresAttention,
            int missingRequirementCount,
            boolean previewReady,
            OffsetDateTime updatedAt,
            OffsetDateTime submittedAt,
            String publicProfilePath,
            List<String> allowedActions
    ) {
    }

    public record WorkspaceProfileResponse(
            UUID draftId,
            String draftReference,
            String publicProfileReference,
            ProviderType profileType,
            String displayName,
            String city,
            String area,
            String ownershipStatus,
            String tenantConsentStatus,
            int draftVersion,
            String contentStatus,
            String readinessStatus,
            int completenessPercentage,
            String moderationStatus,
            String activeSubmissionReference,
            String publicationStatus,
            String effectiveVisibility,
            String platformConnectionStatus,
            String bookingCapability,
            OffsetDateTime lastUpdatedAt,
            String publicationReason,
            List<String> blockingReasons,
            List<String> allowedActions,
            String primaryAction,
            List<String> secondaryActions,
            String lifecycleLabel,
            String attentionLabel,
            String nextActionLabel,
            String publicProfilePath,
            boolean providerActionRequired
    ) {
    }

    public record WorkspaceResponse(
            String contactEmail,
            String contactPhone,
            OffsetDateTime emailVerifiedAt,
            OffsetDateTime phoneVerifiedAt,
            List<ProviderWorkspaceWorkItemResponse> workItems,
            List<WorkspaceApplicationResponse> applications,
            List<WorkspaceApplicationResponse> publishedProfiles,
            List<WorkspaceProfileResponse> profiles,
            int attentionCount,
            int activeProfileCount,
            int readyForReviewCount,
            int underReviewCount,
            int publishedCount,
            int needsAttentionCount,
            List<ProviderType> supportedProviderTypes
    ) {
    }

    public record ProviderWorkspaceWorkItemResponse(
            String workItemType,
            String publicProfileType,
            String workItemReference,
            String publicProfileReference,
            String connectionReference,
            String displayName,
            String city,
            String area,
            String claimStatus,
            String ownershipStatus,
            String reviewStatus,
            String workItemStatus,
            String publicDiscoveryConsent,
            String platformConnectionStatus,
            String publicationStatus,
            String membershipRole,
            OffsetDateTime lastUpdatedAt,
            List<String> allowedActions
    ) {
    }

    public record ProviderClaimReviewResponse(
            String connectionReference,
            String status,
            String pageMode,
            String workItemStatus,
            String reviewStatus,
            OffsetDateTime submittedAt,
            OffsetDateTime reviewedAt,
            OffsetDateTime ownershipUpdatedAt,
            String reason,
            String claimNote,
            String maskedProviderMobile,
            String publicProfileType,
            String displayName,
            String city,
            String area,
            String qualification,
            String specialty,
            Integer yearsOfExperience,
            String tenantConsentStatus,
            String publicProfileStatus,
            String platformConnectionStatus,
            String bookingCapability,
            String ownershipStatus,
            List<String> membershipRoles,
            List<String> disputeStatuses,
            String doctorUserDisplayName,
            List<String> allowedActions
    ) {
    }

    public record ProviderOnboardingAccessResponse(
            UUID applicationId,
            String onboardingToken
    ) {
    }

    public record ProviderWorkspaceStartRequest(
            ProviderType providerType,
            Boolean createNew
    ) {
    }

    public record ProviderWorkspaceStartResponse(
            UUID applicationId,
            String referenceNumber,
            ProviderType providerType,
            ProviderLifecycleStatus status,
            String currentStep,
            String onboardingToken,
            String publicProfilePath
    ) {
    }
}
