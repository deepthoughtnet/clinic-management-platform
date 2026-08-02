package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicationStatus;

public record HealthcareClinicPublicListingChangedV1(
        PublicProviderReference publicReference,
        boolean publicListingEnabled,
        PublicationStatus publicationStatus,
        String reason
) implements ProviderIntegrationEventPayload {
}
