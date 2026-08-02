package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

public record ProviderFactsSnapshot(
        PublicProfileType publicProfileType,
        ProviderSourceReference sourceReference,
        PublicProviderReference publicReference,
        String canonicalSlug,
        String displayName,
        String specialty,
        String qualification,
        Integer experienceYears,
        String area,
        String city,
        String state,
        String country,
        String publicPhone,
        String publicFee,
        List<String> consultationModes,
        BookingCapability bookingCapability,
        AvailabilityState availabilityState,
        PublicationStatus publicationStatus,
        SourceSystem sourceSystem,
        OffsetDateTime projectedAt
) implements Serializable {
}
