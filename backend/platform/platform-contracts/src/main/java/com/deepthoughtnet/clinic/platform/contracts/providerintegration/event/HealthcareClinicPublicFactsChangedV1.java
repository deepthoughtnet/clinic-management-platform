package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderFactsSnapshot;

public record HealthcareClinicPublicFactsChangedV1(
        ProviderFactsSnapshot factsSnapshot,
        String reason
) implements ProviderIntegrationEventPayload {
}
