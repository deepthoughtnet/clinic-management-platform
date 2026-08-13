package com.deepthoughtnet.clinic.discover.providerownership;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileDisputeStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProviderOwnershipModels {
    private ProviderOwnershipModels() {
    }

    public record ClaimIntentRecord(
            UUID id,
            String connectionReference,
            PublicProfileType publicProfileType,
            String publicProfileReference,
            String tenantReference,
            UUID providerAccountId,
            UUID issuerAppUserId,
            long sourceRevision,
            PublicProfileClaimIntentStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime openedAt,
            OffsetDateTime providerAuthenticatedAt,
            OffsetDateTime claimSubmittedAt,
            OffsetDateTime consumedAt,
            OffsetDateTime revokedAt,
            OffsetDateTime rejectedAt,
            String reason,
            String evidenceSnapshotJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record OwnershipRecord(
            UUID id,
            String publicProfileReference,
            PublicProfileType publicProfileType,
            UUID providerAccountId,
            PublicProfileOwnershipStatus status,
            String ownershipMethod,
            String tenantReference,
            long sourceRevision,
            OffsetDateTime verifiedAt,
            OffsetDateTime revokedAt,
            String rejectionReason,
            UUID transferTargetProviderAccountId,
            boolean active,
            String reason,
            String evidenceSnapshotJson,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record MembershipRecord(
            UUID id,
            String publicProfileReference,
            UUID providerAccountId,
            PublicProfileMembershipRole role,
            String status,
            long sourceRevision,
            String reason,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record DisputeRecord(
            UUID id,
            String publicProfileReference,
            PublicProfileType publicProfileType,
            UUID ownershipId,
            String claimIntentReference,
            PublicProfileDisputeStatus status,
            String reason,
            String resolutionReason,
            UUID openedByAppUserId,
            UUID resolvedByAppUserId,
            OffsetDateTime openedAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PublicProfileOwnershipSummary(
            String publicProfileReference,
            PublicProfileType publicProfileType,
            String displayName,
            String city,
            String area,
            String maskedProviderMobile,
            String consentState,
            String publicProfileStatus,
            String platformConnectionStatus,
            String bookingCapability,
            String claimReference,
            String ownershipStatus,
            OffsetDateTime lastUpdatedAt
    ) {
    }

    public record OwnershipSnapshot(
            OwnershipRecord ownership,
            List<MembershipRecord> memberships,
            List<DisputeRecord> disputes
    ) {
    }

    public record OwnershipRepairRecord(
            String publicProfileReference,
            UUID providerAccountId,
            boolean ownershipCreated,
            boolean ownershipUpdated,
            boolean membershipCreated,
            boolean membershipUpdated,
            boolean conflict,
            String conflictReason
    ) {
    }

    public record OwnershipReconciliationRecord(
            String publicProfileReference,
            long historicalClaimCount,
            long activeClaimCount,
            String currentClaimReference,
            String ownershipStatus,
            String currentMembershipStatus,
            long duplicateActiveCount,
            String recommendedAction
    ) {
    }
}
