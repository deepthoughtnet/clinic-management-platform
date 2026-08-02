package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicationStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;

public record DiscoverPublicProfilePublishedV1(
        ProviderSourceReference sourceReference,
        PublicProviderReference publicReference,
        String canonicalSlug,
        PublicationStatus publicationStatus,
        String reason
) implements ProviderIntegrationEventPayload {
}
