package com.deepthoughtnet.clinic.discover.verification;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProviderWorkspaceApplicationRecord(
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
        String publicProfilePath
) {
}
