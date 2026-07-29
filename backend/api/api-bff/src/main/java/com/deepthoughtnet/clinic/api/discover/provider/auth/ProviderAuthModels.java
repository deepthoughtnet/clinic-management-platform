package com.deepthoughtnet.clinic.api.discover.provider.auth;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
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
            String message,
            String developmentCode,
            long expiresInSeconds,
            long resendAfterSeconds,
            String providerName,
            String deliveryReference
    ) {
    }

    public record LoginVerifyRequest(@NotBlank String identifier, @NotBlank String code) {
    }

    public record LoginVerifyResponse(
            boolean verified,
            UUID providerAccountId,
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
            UUID providerAccountId,
            List<WorkspaceApplicationResponse> applications
    ) {
    }
}
