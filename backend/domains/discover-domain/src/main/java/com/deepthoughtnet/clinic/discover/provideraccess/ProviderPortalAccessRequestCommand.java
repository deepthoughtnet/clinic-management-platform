package com.deepthoughtnet.clinic.discover.provideraccess;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;

public record ProviderPortalAccessRequestCommand(
        String fullName,
        String email,
        String mobile,
        ProviderType providerType,
        String providerApplicationReference,
        String note
) {
}
