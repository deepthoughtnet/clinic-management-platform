package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderFactsSnapshot;

public record HealthcareDoctorPublicFactsChangedV1(
        ProviderFactsSnapshot factsSnapshot,
        String reason
) implements ProviderIntegrationEventPayload {
}
