package com.deepthoughtnet.clinic.discover.publicprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderLifecycleStatus;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationRepository;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRepairRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationSubmissionRecord;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderPublicProfileProjectionRepairServiceTest {
    private ProviderApplicationRepository applications;
    private DiscoverProviderAccountRepository providerAccounts;
    private ProviderPublicProfileService publicProfileService;
    private ProviderOwnershipService ownershipService;
    private ProviderPublicProfileModerationService moderationService;
    private ProviderPublicProfileDraftService draftService;
    private ProviderPublicProfileProjectionRepairService service;
    private ProviderApplicationEntity application;

    @BeforeEach
    void setUp() {
        applications = mock(ProviderApplicationRepository.class);
        providerAccounts = mock(DiscoverProviderAccountRepository.class);
        publicProfileService = mock(ProviderPublicProfileService.class);
        ownershipService = mock(ProviderOwnershipService.class);
        moderationService = mock(ProviderPublicProfileModerationService.class);
        draftService = mock(ProviderPublicProfileDraftService.class);
        service = new ProviderPublicProfileProjectionRepairService(
                applications,
                providerAccounts,
                publicProfileService,
                ownershipService,
                moderationService,
                draftService
        );
    }

    @Test
    void repairProviderApplicationBackfillsAllMissingHospitalWorkspaceProjections() {
        UUID providerAccountId = UUID.fromString("6222eead-866b-4675-b74e-75dcd012f4f8");
        String publicProfileReference = "2206731d-3f34-426f-b069-2abca255f988";
        application = historicalApplication(providerAccountId);
        when(applications.findById(application.getId())).thenReturn(Optional.of(application));
        when(providerAccounts.findById(providerAccountId)).thenReturn(Optional.of(DiscoverProviderAccountEntity.create(null, "9876501502")));
        when(publicProfileService.findLifecycleByProviderId(application.getId())).thenReturn(Optional.of(hospitalLifecycle(publicProfileReference)));
        when(moderationService.currentSubmission(publicProfileReference)).thenReturn(Optional.empty());
        when(ownershipService.ensureHistoricalVerifiedOwnership(any(), any(), any()))
                .thenReturn(new OwnershipRepairRecord(publicProfileReference, providerAccountId, true, false, true, false, false, null));
        AtomicReference<PublicProfileDraftWorkspaceRecord> createdDraft = new AtomicReference<>();
        when(draftService.findDraft(publicProfileReference)).thenReturn(Optional.empty());
        when(draftService.createOrLoadDraft(providerAccountId, publicProfileReference)).thenAnswer(invocation -> {
            if (createdDraft.get() == null) {
                createdDraft.set(hospitalDraftWorkspace(providerAccountId, publicProfileReference));
            }
            return createdDraft.get();
        });

        var outcome = service.repairProviderApplication(application.getId());

        assertThat(outcome.conflict()).isFalse();
        assertThat(outcome.draftCreated()).isTrue();
        assertThat(outcome.ownershipCreated()).isTrue();
        assertThat(outcome.membershipCreated()).isTrue();
        verify(draftService).createOrLoadDraft(providerAccountId, publicProfileReference);
    }

    @Test
    void repairProviderApplicationIsIdempotentOnceWorkspaceExists() {
        UUID providerAccountId = UUID.fromString("6222eead-866b-4675-b74e-75dcd012f4f8");
        String publicProfileReference = "2206731d-3f34-426f-b069-2abca255f988";
        application = historicalApplication(providerAccountId);
        when(applications.findById(application.getId())).thenReturn(Optional.of(application));
        when(providerAccounts.findById(providerAccountId)).thenReturn(Optional.of(DiscoverProviderAccountEntity.create(null, "9876501502")));
        when(publicProfileService.findLifecycleByProviderId(application.getId())).thenReturn(Optional.of(hospitalLifecycle(publicProfileReference)));
        when(moderationService.currentSubmission(publicProfileReference)).thenReturn(Optional.empty());
        when(ownershipService.ensureHistoricalVerifiedOwnership(any(), any(), any()))
                .thenReturn(new OwnershipRepairRecord(publicProfileReference, providerAccountId, false, false, false, false, false, null));
        AtomicReference<PublicProfileDraftWorkspaceRecord> createdDraft = new AtomicReference<>();
        when(draftService.findDraft(publicProfileReference)).thenAnswer(invocation -> Optional.ofNullable(createdDraft.get()));
        when(draftService.createOrLoadDraft(providerAccountId, publicProfileReference)).thenAnswer(invocation -> {
            if (createdDraft.get() == null) {
                createdDraft.set(hospitalDraftWorkspace(providerAccountId, publicProfileReference));
            }
            return createdDraft.get();
        });

        var first = service.repairProviderApplication(application.getId());
        var second = service.repairProviderApplication(application.getId());

        assertThat(first.conflict()).isFalse();
        assertThat(second.conflict()).isFalse();
        assertThat(first.draftCreated()).isTrue();
        assertThat(second.draftCreated()).isFalse();
        assertThat(first.repaired()).isTrue();
        assertThat(second.repaired()).isFalse();
        verify(draftService, times(1)).createOrLoadDraft(providerAccountId, publicProfileReference);
    }

    @Test
    void repairProviderApplicationSkipsDraftBootstrapWhileSubmissionIsUnderReview() {
        UUID providerAccountId = UUID.fromString("6222eead-866b-4675-b74e-75dcd012f4f8");
        String publicProfileReference = "2206731d-3f34-426f-b069-2abca255f988";
        application = historicalApplication(providerAccountId);
        when(applications.findById(application.getId())).thenReturn(Optional.of(application));
        when(providerAccounts.findById(providerAccountId)).thenReturn(Optional.of(DiscoverProviderAccountEntity.create(null, "9876501502")));
        when(publicProfileService.findLifecycleByProviderId(application.getId())).thenReturn(Optional.of(hospitalLifecycle(publicProfileReference)));
        when(moderationService.currentSubmission(publicProfileReference)).thenReturn(Optional.of(activeSubmission(providerAccountId, publicProfileReference)));
        when(ownershipService.ensureHistoricalVerifiedOwnership(any(), any(), any()))
                .thenReturn(new OwnershipRepairRecord(publicProfileReference, providerAccountId, false, false, false, false, false, null));
        when(draftService.findDraft(publicProfileReference)).thenReturn(Optional.of(hospitalDraftWorkspace(providerAccountId, publicProfileReference)));

        var outcome = service.repairProviderApplication(application.getId());

        assertThat(outcome.conflict()).isFalse();
        assertThat(outcome.activeReviewSkipped()).isTrue();
        assertThat(outcome.draftCreated()).isFalse();
        assertThat(outcome.repaired()).isFalse();
        verify(draftService, times(0)).createOrLoadDraft(providerAccountId, publicProfileReference);
    }

    @Test
    void repairProviderApplicationSkipsConflictingOwnershipWithoutCreatingDraft() {
        UUID providerAccountId = UUID.fromString("6222eead-866b-4675-b74e-75dcd012f4f8");
        String publicProfileReference = "2206731d-3f34-426f-b069-2abca255f988";
        application = historicalApplication(providerAccountId);
        when(applications.findById(application.getId())).thenReturn(Optional.of(application));
        when(providerAccounts.findById(providerAccountId)).thenReturn(Optional.of(DiscoverProviderAccountEntity.create(null, "9876501502")));
        when(publicProfileService.findLifecycleByProviderId(application.getId())).thenReturn(Optional.of(hospitalLifecycle(publicProfileReference)));
        when(ownershipService.ensureHistoricalVerifiedOwnership(any(), any(), any()))
                .thenReturn(new OwnershipRepairRecord(publicProfileReference, providerAccountId, false, false, false, false, true, "conflict"));

        var outcome = service.repairProviderApplication(application.getId());

        assertThat(outcome.conflict()).isTrue();
        verify(draftService, org.mockito.Mockito.never()).findDraft(any());
        verify(draftService, org.mockito.Mockito.never()).createOrLoadDraft(any(), any());
    }

    private static ProviderApplicationEntity historicalApplication(UUID providerAccountId) {
        ProviderApplicationEntity entity = ProviderApplicationEntity.create(
                UUID.fromString("2206731d-3f34-426f-b069-2abca255f988"),
                "JHS-2026-2206731D",
                ProviderType.HOSPITAL,
                "token",
                "hospital@example.test",
                "9876501502",
                "password",
                true,
                true
        );
        entity.setProviderAccountId(providerAccountId);
        entity.setStatus(ProviderLifecycleStatus.PUBLISHED);
        entity.setDisplayName("Jeevanam Multispeciality Hospital");
        entity.setLegalName("Jeevanam Multispeciality Hospital");
        return entity;
    }

    private static PublicProfileLifecycleRecord hospitalLifecycle(String publicProfileReference) {
        return new PublicProfileLifecycleRecord(
                UUID.fromString(publicProfileReference),
                ProviderType.HOSPITAL,
                "DISCOVER_ONBOARDING_APPLICATION",
                publicProfileReference,
                0L,
                OffsetDateTime.now(),
                "jeevanam-multispeciality-hospital",
                "Jeevanam Multispeciality Hospital",
                "Pune",
                "Pune",
                "CALL_TO_BOOK",
                "PUBLISHED",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                0L,
                "/discover/hospitals/jeevanam-multispeciality-hospital"
        );
    }

    private static PublicProfileDraftWorkspaceRecord hospitalDraftWorkspace(UUID providerAccountId, String publicProfileReference) {
        return new PublicProfileDraftWorkspaceRecord(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                publicProfileReference,
                ProviderType.HOSPITAL,
                providerAccountId,
                "VERIFIED",
                "ENABLED",
                "PUBLISHED",
                "READY_FOR_REVIEW",
                "READY",
                100,
                1,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                "Jeevanam Multispeciality Hospital",
                "jeevanam-multispeciality-hospital",
                "Pune",
                "Pune",
                "Maharashtra",
                "India",
                "9876501502",
                "hospital.b@jeevanam.test",
                "https://jeevanam.example/hospital",
                null,
                "JHS-2026-2206731D",
                null,
                "DISCOVER_ONBOARDING_APPLICATION",
                publicProfileReference,
                0L,
                OffsetDateTime.now(),
                "/discover/hospitals/jeevanam-multispeciality-hospital",
                List.of(),
                List.of(),
                null,
                List.of(),
                Map.of()
        );
    }

    private static PublicProfileModerationSubmissionRecord activeSubmission(UUID providerAccountId, String publicProfileReference) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        return new PublicProfileModerationSubmissionRecord(
                UUID.randomUUID(),
                "submission-1",
                publicProfileReference,
                ProviderType.HOSPITAL,
                "draft-1",
                3,
                "UNDER_REVIEW",
                "PUBLISHED",
                "ENABLED",
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                providerAccountId,
                now,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                true,
                null,
                now,
                null,
                now,
                now,
                "VISIBLE",
                "Published profile is publicly visible.",
                "/discover/hospitals/jeevanam-multispeciality-hospital",
                List.of(),
                List.of("BACK_TO_WORKSPACE", "VIEW_SUBMITTED_PROFILE"),
                List.of("BACK_TO_WORKSPACE", "VIEW_SUBMITTED_PROFILE")
        );
    }
}
