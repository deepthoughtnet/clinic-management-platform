package com.deepthoughtnet.clinic.api.discover.provider.access;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ProviderAccessModels {
    private ProviderAccessModels() {
    }

    public record ProviderAccessRequestSubmitRequest(
            @NotBlank String fullName,
            @NotBlank String email,
            @NotBlank String mobile,
            @NotNull ProviderType providerType,
            String providerApplicationReference,
            String note
    ) {
    }

    public record ProviderAccessRequestDecisionRequest(String reason, String providerApplicationReference) {
    }

    public record ProviderAccessRequestResponse(
            UUID id,
            ProviderType providerType,
            String fullName,
            String email,
            String mobile,
            String providerApplicationReference,
            String note,
            String status,
            String rejectionReason,
            UUID linkedProviderAccountId,
            String linkedProviderAccountDisplayName,
            String linkedProviderApplicationReference,
            UUID reviewedBy,
            String reviewedByDisplayName,
            String temporaryAccessCode,
            OffsetDateTime requestedAt,
            OffsetDateTime reviewedAt,
            OffsetDateTime approvedAt,
            OffsetDateTime revokedAt,
            OffsetDateTime accessCodeIssuedAt,
            OffsetDateTime accessCodeExpiresAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            long version
    ) {
    }

    public record ProviderAccessLoginRequest(
            @NotBlank String identifier,
            @NotBlank String accessCode
    ) {
    }
}
