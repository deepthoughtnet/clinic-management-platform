package com.deepthoughtnet.clinic.api.discover.reference.dto;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.reference.DiscoverReferenceCategory;
import java.util.List;
import java.util.UUID;

public record DiscoverReferenceOptionResponse(
        UUID id,
        String code,
        String displayName,
        List<ProviderType> providerTypes,
        int displayOrder,
        boolean active,
        DiscoverReferenceCategory category
) {
}
