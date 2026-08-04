package com.deepthoughtnet.clinic.discover.providerownership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipSnapshot;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileClaimIntentRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileDisputeRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileMembershipEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileMembershipRepository;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileOwnershipEntity;
import com.deepthoughtnet.clinic.discover.providerownership.db.PublicProfileOwnershipRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProviderOwnershipServiceTest {
    private ProviderOwnershipService service;
    private PublicProfileClaimIntentRepository claimIntentRepository;
    private PublicProfileOwnershipRepository ownershipRepository;
    private PublicProfileMembershipRepository membershipRepository;
    private PublicProfileDisputeRepository disputeRepository;
    private DiscoverProviderAccountRepository providerAccountRepository;
    private AtomicReference<PublicProfileClaimIntentEntity> savedIntent;
    private List<PublicProfileMembershipEntity> savedMemberships;

    @BeforeEach
    void setUp() {
        ProviderOwnershipLifecyclePolicy lifecyclePolicy = new ProviderOwnershipLifecyclePolicy();
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        providerAccountRepository = mock(DiscoverProviderAccountRepository.class);
        claimIntentRepository = mock(PublicProfileClaimIntentRepository.class);
        ownershipRepository = mock(PublicProfileOwnershipRepository.class);
        membershipRepository = mock(PublicProfileMembershipRepository.class);
        disputeRepository = mock(PublicProfileDisputeRepository.class);
        savedIntent = new AtomicReference<>();
        savedMemberships = new java.util.ArrayList<>();
        when(claimIntentRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        when(claimIntentRepository.findActiveByPublicProfileReferenceOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        when(ownershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        service = new ProviderOwnershipService(
                lifecyclePolicy,
                publicProfileService,
                providerAccountRepository,
                claimIntentRepository,
                ownershipRepository,
                membershipRepository,
                disputeRepository
        );
    }

    @Test
    void clinicClaimIntentUsesOpaqueConnectionReference() {
        when(claimIntentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var intent = service.createClinicClaimIntent(UUID.fromString("11111111-1111-1111-1111-111111111111"), UUID.fromString("22222222-2222-2222-2222-222222222222"), "Healthcare initiated connection");

        assertThatCode(() -> UUID.fromString(intent.connectionReference())).doesNotThrowAnyException();
        assertThat(intent.publicProfileType()).isEqualTo(PublicProfileType.CLINIC);
        assertThat(intent.publicProfileReference()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(intent.tenantReference()).isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void submitClaimCreatesPendingOwnershipAndOwnerMembership() {
        UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID providerAccountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID issuerAppUserId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        var account = DiscoverProviderAccountEntity.create(null, "9876504321");
        account.markPhoneVerified();
        when(providerAccountRepository.findById(providerAccountId)).thenReturn(Optional.of(account));
        when(claimIntentRepository.save(any())).thenAnswer(invocation -> {
            PublicProfileClaimIntentEntity entity = invocation.getArgument(0);
            savedIntent.set(entity);
            return entity;
        });
        when(claimIntentRepository.findByConnectionReference(any())).thenAnswer(invocation -> Optional.ofNullable(savedIntent.get()));
        when(ownershipRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId)).thenReturn(List.of());
        when(ownershipRepository.save(any(PublicProfileOwnershipEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(membershipRepository.findByPublicProfileReferenceAndProviderAccountIdAndRole(any(), any(), any())).thenReturn(Optional.empty());
        when(membershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(any())).thenAnswer(invocation -> List.copyOf(savedMemberships));
        when(membershipRepository.save(any(PublicProfileMembershipEntity.class))).thenAnswer(invocation -> {
            PublicProfileMembershipEntity membership = invocation.getArgument(0);
            savedMemberships.removeIf(existing -> existing.getId().equals(membership.getId()));
            savedMemberships.add(membership);
            return membership;
        });
        when(disputeRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc(any())).thenReturn(List.of());

        var intent = service.createClaimIntent(
                "opaque-claim-reference",
                PublicProfileType.CLINIC,
                tenantId.toString(),
                tenantId.toString(),
                issuerAppUserId,
                "Healthcare initiated connection"
        );

        OwnershipSnapshot snapshot = service.submitClaim(
                intent.connectionReference(),
                providerAccountId,
                issuerAppUserId,
                "{\"evidence\":true}",
                "Claim submitted"
        );

        assertThat(snapshot.ownership().status().name()).isEqualTo("CLAIM_PENDING");
        assertThat(snapshot.ownership().active()).isFalse();
        assertThat(snapshot.ownership().evidenceSnapshotJson()).isEqualTo("{\"evidence\":true}");
        assertThat(snapshot.memberships()).singleElement().satisfies(membership -> {
            assertThat(membership.role()).isEqualTo(PublicProfileMembershipRole.OWNER);
            assertThat(membership.status()).isEqualTo("ACTIVE");
        });
        assertThat(snapshot.disputes()).isEmpty();
        assertThat(savedIntent.get().getEvidenceSnapshotJson()).isEqualTo("{\"evidence\":true}");
        assertThat(service.maskedProviderMobile(providerAccountId)).hasValue("******4321");
    }

    @Test
    void listClaimIntentsCollapsesDuplicateHistoricalRowsByProfileReference() {
        UUID providerAccountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        PublicProfileClaimIntentEntity older = PublicProfileClaimIntentEntity.create(
                "older-connection",
                PublicProfileType.CLINIC,
                "clinic-profile-ref",
                "tenant-ref",
                providerAccountId,
                0L,
                java.time.OffsetDateTime.parse("2026-08-03T10:00:00Z"),
                "older"
        );
        older.authenticate(providerAccountId);
        PublicProfileClaimIntentEntity newer = PublicProfileClaimIntentEntity.create(
                "newer-connection",
                PublicProfileType.CLINIC,
                "clinic-profile-ref",
                "tenant-ref",
                providerAccountId,
                0L,
                java.time.OffsetDateTime.parse("2026-08-03T12:00:00Z"),
                "newer"
        );
        newer.submit(providerAccountId, "{\"evidence\":true}");
        when(claimIntentRepository.findByProviderAccountIdOrderByUpdatedAtDesc(providerAccountId)).thenReturn(List.of(newer, older));

        List<ProviderOwnershipModels.ClaimIntentRecord> claimIntents = service.listClaimIntents(providerAccountId);

        assertThat(claimIntents).hasSize(1);
        assertThat(claimIntents.get(0).connectionReference()).isEqualTo("newer-connection");
        assertThat(claimIntents.get(0).status()).isEqualTo(com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileClaimIntentStatus.CLAIM_SUBMITTED);
    }

    @Test
    void createClinicClaimIntentRejectsVerifiedOwnership() {
        when(ownershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc("11111111-1111-1111-1111-111111111111"))
                .thenReturn(List.of(ownership("11111111-1111-1111-1111-111111111111", PublicProfileOwnershipStatus.VERIFIED)));

        assertThatThrownBy(() -> service.createClinicClaimIntent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Healthcare initiated connection"
        ))
                .isInstanceOf(ProviderOwnershipConflictException.class)
                .hasMessageContaining("verified");
    }

    @Test
    void createClinicClaimIntentRejectsPendingOwnership() {
        when(ownershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc("11111111-1111-1111-1111-111111111111"))
                .thenReturn(List.of(ownership("11111111-1111-1111-1111-111111111111", PublicProfileOwnershipStatus.CLAIM_PENDING)));

        assertThatThrownBy(() -> service.createClinicClaimIntent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Healthcare initiated connection"
        ))
                .isInstanceOf(ProviderOwnershipConflictException.class)
                .hasMessageContaining("active claim");
    }

    @Test
    void createClinicClaimIntentReturnsExistingActiveClaim() {
        UUID providerAccountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        PublicProfileClaimIntentEntity active = PublicProfileClaimIntentEntity.create(
                "existing-connection",
                PublicProfileType.CLINIC,
                "11111111-1111-1111-1111-111111111111",
                "11111111-1111-1111-1111-111111111111",
                providerAccountId,
                0L,
                java.time.OffsetDateTime.parse("2026-08-03T12:00:00Z"),
                "active"
        );
        active.authenticate(providerAccountId);
        when(claimIntentRepository.findActiveByPublicProfileReferenceOrderByUpdatedAtDesc("11111111-1111-1111-1111-111111111111"))
                .thenReturn(List.of(active));
        when(ownershipRepository.findByPublicProfileReferenceOrderByUpdatedAtDesc("11111111-1111-1111-1111-111111111111"))
                .thenReturn(List.of());

        var intent = service.createClinicClaimIntent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Healthcare initiated connection"
        );

        assertThat(intent.connectionReference()).isEqualTo("existing-connection");
    }

    @Test
    void approveOwnershipRejectsStaleRevision() {
        PublicProfileOwnershipEntity ownership = ownership("11111111-1111-1111-1111-111111111111", PublicProfileOwnershipStatus.CLAIM_PENDING);
        ReflectionTestUtils.setField(ownership, "rowVersion", 7L);
        when(ownershipRepository.findById(ownership.getId())).thenReturn(Optional.of(ownership));

        assertThatThrownBy(() -> service.approveOwnership(ownership.getId(), UUID.fromString("22222222-2222-2222-2222-222222222222"), "approve", 3L))
                .isInstanceOf(ProviderOwnershipConflictException.class)
                .hasMessageContaining("changed since it was loaded");
    }

    @Test
    void verifiedApprovalRetryDoesNotRegressState() {
        PublicProfileOwnershipEntity ownership = ownership("11111111-1111-1111-1111-111111111111", PublicProfileOwnershipStatus.VERIFIED);
        when(ownershipRepository.findById(ownership.getId())).thenReturn(Optional.of(ownership));
        when(membershipRepository.findByPublicProfileReferenceAndProviderAccountIdAndRole(any(), any(), any())).thenReturn(Optional.empty());

        var updated = service.approveOwnership(ownership.getId(), UUID.fromString("22222222-2222-2222-2222-222222222222"), "approve");

        assertThat(updated.status()).isEqualTo(PublicProfileOwnershipStatus.VERIFIED);
    }

    @Test
    void verifiedRejectAttemptIsRejected() {
        PublicProfileOwnershipEntity ownership = ownership("11111111-1111-1111-1111-111111111111", PublicProfileOwnershipStatus.VERIFIED);
        when(ownershipRepository.findById(ownership.getId())).thenReturn(Optional.of(ownership));

        assertThatThrownBy(() -> service.rejectOwnership(ownership.getId(), UUID.fromString("22222222-2222-2222-2222-222222222222"), "reject"))
                .isInstanceOf(ProviderOwnershipConflictException.class)
                .hasMessageContaining("Only a pending ownership can be rejected.");
    }

    private static PublicProfileOwnershipEntity ownership(String publicProfileReference, PublicProfileOwnershipStatus status) {
        PublicProfileOwnershipEntity entity = PublicProfileOwnershipEntity.create(
                publicProfileReference,
                PublicProfileType.CLINIC,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "HEALTHCARE_INITIATED_CONNECTION",
                publicProfileReference,
                0L,
                "seed"
        );
        if (status == PublicProfileOwnershipStatus.VERIFIED) {
            entity.markVerified("verified");
        } else if (status == PublicProfileOwnershipStatus.CLAIM_PENDING) {
            entity.markClaimPending("pending");
        }
        return entity;
    }
}
