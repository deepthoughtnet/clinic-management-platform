package com.deepthoughtnet.clinic.discover.provideraccess;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProviderPortalAccessRequestRecord(
        UUID id,
        ProviderPortalAccessRequestType requestType,
        ProviderType providerType,
        String fullName,
        String email,
        String mobile,
        String providerApplicationReference,
        String note,
        ProviderPortalAccessRequestStatus status,
        String rejectionReason,
        UUID linkedProviderAccountId,
        String linkedProviderAccountDisplayName,
        String linkedProviderApplicationReference,
        UUID reviewedBy,
        String reviewedByDisplayName,
        String temporaryAccessCode,
        OffsetDateTime requestedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime approvedAt,
        OffsetDateTime revokedAt,
        OffsetDateTime accessCodeIssuedAt,
        OffsetDateTime accessCodeExpiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long version
) {
}
