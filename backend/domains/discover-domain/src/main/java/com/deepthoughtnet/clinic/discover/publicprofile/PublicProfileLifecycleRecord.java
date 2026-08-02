package com.deepthoughtnet.clinic.discover.publicprofile;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicProfileLifecycleRecord(
        UUID providerId,
        ProviderType providerType,
        String sourceSystem,
        String sourceEntityReference,
        long sourceRevision,
        OffsetDateTime sourceUpdatedAt,
        String canonicalSlug,
        String displayName,
        String city,
        String area,
        String bookingMode,
        String publicationStatus,
        OffsetDateTime projectedAt,
        OffsetDateTime publishedAt,
        long connectionRevision,
        String publicPath
) {
}
