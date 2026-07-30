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
            OffsetDateTime updatedAt,
            OffsetDateTime submittedAt,
            String publicProfilePath
    ) {
    }

    public record WorkspaceResponse(
            String contactEmail,
            String contactPhone,
            OffsetDateTime emailVerifiedAt,
            OffsetDateTime phoneVerifiedAt,
            List<WorkspaceApplicationResponse> applications
    ) {
    }

    public record ProviderOnboardingAccessResponse(
            UUID applicationId,
            String onboardingToken
    ) {
    }
}
