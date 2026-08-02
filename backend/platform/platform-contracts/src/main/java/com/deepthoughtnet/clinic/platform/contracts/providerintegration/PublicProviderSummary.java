package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record PublicProviderSummary(
        PublicProfileType publicProfileType,
        PublicProviderReference publicReference,
        String canonicalSlug,
        String displayName,
        String area,
        String city,
        String state,
        String country,
        String publicPhone,
        String publicFee,
        BookingCapability bookingCapability,
        AvailabilityState availabilityState,
        PublicationStatus publicationStatus,
        SourceSystem sourceSystem,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt,
        OffsetDateTime projectedAt
) implements Serializable {
}
