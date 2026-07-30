package com.deepthoughtnet.clinic.discover.reference;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.util.List;
import java.util.UUID;

public record DiscoverReferenceOptionRecord(
        UUID id,
        DiscoverReferenceCategory category,
        String code,
        String displayName,
        List<ProviderType> providerTypes,
        int displayOrder,
        boolean active
) {
}
