package com.deepthoughtnet.clinic.discover.publicprofilemoderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftFieldSourceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileSubmissionEligibilityRecord;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProviderPublicProfileModerationServiceTest {
    private static final UUID PROVIDER_ACCOUNT_ID = UUID.fromString("8e5a6d56-08f8-47f1-99f4-f79b22aaef48");
    private static final UUID REVIEWER_ID = UUID.fromString("18b7f39c-7dfc-4f1f-9f93-3d7b2f0d2f3b");
    private static final String PUBLIC_PROFILE_REFERENCE = "407dbc68-107d-4f64-83c8-6499e50e5c78";
    private static final String SUBMISSION_REFERENCE = "submission-1";
    private static final String DRAFT_REFERENCE = "draft-1";
    private static final String LOGO_REFERENCE = "3d7b60eb-e869-3184-aaea-4a9719fb2cb2";
    private static final String COVER_REFERENCE = "527da827-4f73-31db-8298-b31a2688772f";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-04T10:15:30Z");

    @Mock
    private ProviderPublicProfileDraftService draftService;
    @Mock
    private ProviderOwnershipService ownershipService;
    @Mock
    private ProviderPublicProfileService publicProfileService;
    @Mock
    private DiscoverPublicProfileSubmissionRepository submissions;
    @Mock
    private DiscoverPublicProfileReviewFindingRepository findings;
    @Mock
    private DiscoverPublicProfilePublicationRepository publications;
    @Mock
    private ObjectStorageService storageService;

    private ProviderPublicProfileModerationService service;

    @BeforeEach
    void setUp() {
        service = new ProviderPublicProfileModerationService(
                draftService,
                ownershipService,
                publicProfileService,
                submissions,
                findings,
                publications,
                storageService,
                new ObjectMapper()
        );
        lenient().when(submissions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(findings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(publications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestChangesRejectsEmptyPayload() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("SUBMITTED");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.requestChanges(SUBMISSION_REFERENCE, REVIEWER_ID, 0L, null, List.of()))
                .isInstanceOf(com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException.class)
                .satisfies(error -> assertThat(((com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException) error).getCode()).isEqualTo("invalid_moderation_transition"));
        verify(findings, never()).save(any());
    }

    @Test
    void requestChangesSkipsDuplicateFindings() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("SUBMITTED");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        DiscoverPublicProfileReviewFindingEntity existing = DiscoverPublicProfileReviewFindingEntity.create(
                UUID.fromString("aaaaaaa1-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "finding-1",
                SUBMISSION_REFERENCE,
                "about",
                "description",
                "DESCRIPTION",
                "BLOCKING",
                true,
                "Add more detail",
                "OPEN",
                NOW
        );
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(SUBMISSION_REFERENCE)).thenReturn(List.of(existing));

        var updated = service.requestChanges(
                SUBMISSION_REFERENCE,
                REVIEWER_ID,
                0L,
                "Add more detail",
                List.of(Map.of(
                        "section", "about",
                        "field", "description",
                        "category", "DESCRIPTION",
                        "severity", "BLOCKING",
                        "required", true,
                        "reviewerNote", "Add more detail"
                ))
        );

        assertThat(updated.moderationStatus()).isEqualTo("CHANGES_REQUESTED");
        verify(findings, never()).save(any());
    }

    @Test
    void approveRejectsSubmissionUnlessUnderReview() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("SUBMITTED");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.approve(SUBMISSION_REFERENCE, REVIEWER_ID, 0L, "Approved"))
                .isInstanceOf(com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException.class)
                .satisfies(error -> assertThat(((com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException) error).getCode()).isEqualTo("invalid_moderation_transition"));
    }

    @Test
    void approvedSubmissionExposesPublishAllowedAction() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(anyString())).thenReturn(List.of());

        var record = service.findSubmissionByReference(SUBMISSION_REFERENCE).orElseThrow();

        assertThat(record.moderationStatus()).isEqualTo("APPROVED");
        assertThat(record.allowedActions()).contains("PUBLISH_PROFILE");
        assertThat(record.allowedActions()).doesNotContain("APPROVE_SUBMISSION");
    }

    @Test
    void submittedSnapshotContainsPersistedTimings() {
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.getDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(timedDraft());
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of());

        var record = service.submitForReview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);

        assertThat(record.contentSnapshot()).containsKey("timings");
        assertThat(record.contentSnapshot().get("timings")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) record.contentSnapshot().get("timings")).get("timezone")).isEqualTo("Asia/Kolkata");
    }

    @Test
    void reviewDtoMapsSubmittedTimings() {
        AtomicReference<DiscoverPublicProfileSubmissionEntity> saved = new AtomicReference<>();
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.getDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(timedDraft());
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of());
        when(submissions.save(any())).thenAnswer(invocation -> {
            DiscoverPublicProfileSubmissionEntity entity = invocation.getArgument(0);
            saved.set(entity);
            return entity;
        });
        when(submissions.findBySubmissionReference(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(anyString())).thenReturn(List.of());

        var submission = service.submitForReview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);
        var loaded = service.findSubmissionByReference(submission.submissionReference()).orElseThrow();

        assertThat(loaded.contentSnapshot()).containsKey("timings");
        assertThat(loaded.providerAllowedActions()).contains("VIEW_SUBMITTED_PROFILE");
        assertThat(((Map<?, ?>) loaded.contentSnapshot().get("timings")).get("timezone")).isEqualTo("Asia/Kolkata");
        assertThat(((List<?>) ((Map<?, ?>) loaded.contentSnapshot().get("timings")).get("intervals"))).isNotEmpty();
    }

    @Test
    void submittedSnapshotRemainsImmutableAfterDraftEdit() {
        AtomicReference<PublicProfileDraftWorkspaceRecord> draft = new AtomicReference<>(timedDraft());
        AtomicReference<DiscoverPublicProfileSubmissionEntity> savedSubmission = new AtomicReference<>();
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.getDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenAnswer(invocation -> draft.get());
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of());
        when(submissions.save(any())).thenAnswer(invocation -> {
            DiscoverPublicProfileSubmissionEntity entity = invocation.getArgument(0);
            savedSubmission.set(entity);
            return entity;
        });
        when(submissions.findBySubmissionReference(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> Optional.ofNullable(savedSubmission.get()));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(anyString())).thenReturn(List.of());

        var submitted = service.submitForReview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);
        draft.set(timedDraftWithUpdatedHours());
        var loaded = service.findSubmissionByReference(submitted.submissionReference()).orElseThrow();

        assertThat(((Map<?, ?>) loaded.contentSnapshot().get("timings")).get("timezone")).isEqualTo("Asia/Kolkata");
        assertThat(loaded.contentSnapshot().get("timings").toString()).contains("09:00");
    }

    @Test
    void startReviewAssignsReviewer() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("SUBMITTED");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));

        var reviewed = service.startReview(
                SUBMISSION_REFERENCE,
                REVIEWER_ID,
                "platform.admin@clinic.local",
                "Platform Admin",
                "platform.admin@clinic.local",
                0L,
                "Begin review"
        );

        assertThat(reviewed.assignedReviewerId()).isEqualTo(REVIEWER_ID);
        assertThat(reviewed.assignedReviewerReference()).isEqualTo("platform.admin@clinic.local");
        assertThat(reviewed.assignedReviewerDisplayName()).isEqualTo("Platform Admin");
        assertThat(reviewed.assignedReviewerEmail()).isEqualTo("platform.admin@clinic.local");
        assertThat(reviewed.moderationStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(reviewed.allowedActions()).contains("APPROVE_SUBMISSION");
        assertThat(reviewed.allowedActions()).doesNotContain("START_REVIEW");
    }

    @Test
    void repeatedStartReviewDoesNotReplaceReviewer() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, "platform.admin@clinic.local", "Platform Admin", "platform.admin@clinic.local", NOW, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));

        var reviewed = service.startReview(
                SUBMISSION_REFERENCE,
                UUID.fromString("99999999-9999-9999-9999-999999999999"),
                "another.reviewer@clinic.local",
                "Another Reviewer",
                "another.reviewer@clinic.local",
                entity.getModerationRevision(),
                "Begin review again"
        );

        assertThat(reviewed.assignedReviewerId()).isEqualTo(REVIEWER_ID);
        assertThat(reviewed.assignedReviewerReference()).isEqualTo("platform.admin@clinic.local");
        assertThat(reviewed.assignedReviewerDisplayName()).isEqualTo("Platform Admin");
        assertThat(reviewed.assignedReviewerEmail()).isEqualTo("platform.admin@clinic.local");
        assertThat(reviewed.assignedAt()).isEqualTo(NOW);
        assertThat(reviewed.moderationRevision()).isEqualTo(entity.getModerationRevision());
    }

    @Test
    void providerReviewStatusReturnsSubmissionLifecycle() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("SUBMITTED");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(SUBMISSION_REFERENCE)).thenReturn(List.of());

        var submission = service.findSubmissionByReference(SUBMISSION_REFERENCE).orElseThrow();

        assertThat(submission.moderationStatus()).isEqualTo("SUBMITTED");
        assertThat(submission.providerAllowedActions()).containsExactly("BACK_TO_WORKSPACE", "VIEW_SUBMITTED_PROFILE");
    }

    @Test
    void providerCanViewReviewStatusDuringActiveReview() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(SUBMISSION_REFERENCE)).thenReturn(List.of());

        var submission = service.findSubmissionByReference(SUBMISSION_REFERENCE).orElseThrow();

        assertThat(submission.moderationStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(submission.providerAllowedActions()).contains("VIEW_SUBMITTED_PROFILE");
        assertThat(submission.assignedReviewerId()).isEqualTo(REVIEWER_ID);
    }

    @Test
    void changesRequestedSubmissionRemainsVisibleInQueueAndProfileLookup() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.requestChanges(REVIEWER_ID, NOW, "Please revise the timings.", NOW);
        when(submissions.findAll()).thenReturn(List.of(entity));
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(entity));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(SUBMISSION_REFERENCE)).thenReturn(List.of());

        var queue = service.listQueue();
        var submission = service.findSubmission(PUBLIC_PROFILE_REFERENCE).orElseThrow();

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).moderationStatus()).isEqualTo("CHANGES_REQUESTED");
        assertThat(submission.moderationStatus()).isEqualTo("CHANGES_REQUESTED");
        assertThat(submission.current()).isFalse();
    }

    @Test
    void submissionEligibilityAllowsResubmissionWhenDraftDiffersFromRequestedChangesSnapshot() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.requestChanges(REVIEWER_ID, NOW, "Please revise the timings.", NOW);
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.getDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(timedDraft());
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(entity));

        PublicProfileSubmissionEligibilityRecord eligibility = service.submissionEligibility(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);

        assertThat(eligibility.submissionEligible()).isTrue();
        assertThat(eligibility.submissionBlockers()).doesNotContain("RESUBMISSION_REQUIRED", "ACTIVE_SUBMISSION_EXISTS");
        assertThat(eligibility.allowedActions()).contains("SUBMIT_FOR_REVIEW");
        assertThat(eligibility.moderationStatus()).isEqualTo("CHANGES_REQUESTED");
    }

    @Test
    void submitForReviewCreatesNewSubmissionAfterRequestedChangesAreAddressed() {
        DiscoverPublicProfileSubmissionEntity existing = submittedEntity("UNDER_REVIEW");
        existing.requestChanges(REVIEWER_ID, NOW, "Please revise the timings.", NOW);
        AtomicReference<DiscoverPublicProfileSubmissionEntity> savedSubmission = new AtomicReference<>();
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.getDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(timedDraft());
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(existing));
        when(submissions.save(any())).thenAnswer(invocation -> {
            DiscoverPublicProfileSubmissionEntity entity = invocation.getArgument(0);
            savedSubmission.set(entity);
            return entity;
        });
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(anyString())).thenReturn(List.of());

        var submitted = service.submitForReview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);

        assertThat(submitted.moderationStatus()).isEqualTo("SUBMITTED");
        assertThat(submitted.submittedDraftVersion()).isEqualTo(4);
        assertThat(submitted.submissionReference()).isNotEqualTo(SUBMISSION_REFERENCE);
        assertThat(submitted.publicProfileReference()).isEqualTo(PUBLIC_PROFILE_REFERENCE);
    }

    @Test
    void addFindingPersistsAgainstSubmissionVersion() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, "platform.admin@clinic.local", "Platform Admin", "platform.admin@clinic.local", NOW, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        AtomicReference<DiscoverPublicProfileReviewFindingEntity> savedFinding = new AtomicReference<>();
        when(findings.save(any())).thenAnswer(invocation -> {
            DiscoverPublicProfileReviewFindingEntity finding = invocation.getArgument(0);
            savedFinding.set(finding);
            return finding;
        });
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(SUBMISSION_REFERENCE)).thenAnswer(invocation ->
                savedFinding.get() == null ? List.of() : List.of(savedFinding.get()));

        var submission = service.addFinding(
                SUBMISSION_REFERENCE,
                REVIEWER_ID,
                "platform.admin@clinic.local",
                "Platform Admin",
                "platform.admin@clinic.local",
                entity.getModerationRevision(),
                Map.of(
                        "section", "Timings",
                        "field", "hours",
                        "category", "Timing",
                        "severity", "Warning",
                        "required", true,
                        "providerFacingMessage", "Please confirm the Saturday schedule.",
                        "internalNote", "Captured while checking the immutable snapshot."
                )
        );

        assertThat(submission.moderationStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(submission.findings()).hasSize(1);
        assertThat(submission.findings().get(0).providerFacingMessage()).isEqualTo("Please confirm the Saturday schedule.");
        assertThat(submission.findings().get(0).internalNote()).isEqualTo("Captured while checking the immutable snapshot.");
        assertThat(submission.moderationRevision()).isEqualTo(entity.getModerationRevision());
    }

    @Test
    void submissionMediaContentUsesImmutableSnapshotStorageKey() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntityWithMedia("SUBMITTED");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(storageService.getObjectBytes("review-logo-key")).thenReturn(new byte[] {1, 2, 3});

        var content = service.submissionMediaContent(SUBMISSION_REFERENCE, LOGO_REFERENCE);

        assertThat(content.mediaReference()).isEqualTo(LOGO_REFERENCE);
        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.originalFilename()).isEqualTo("logo.png");
        assertThat(content.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void providerSubmissionMediaContentAllowsOwnerDuringUnderReviewWithoutDraftLookup() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntityWithMedia("UNDER_REVIEW");
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(ownershipService.listMemberships(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(activeOwnerMembership()));
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(storageService.getObjectBytes("review-logo-key")).thenReturn(new byte[] {1, 2, 3});

        var content = service.providerSubmissionMediaContent(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, SUBMISSION_REFERENCE, LOGO_REFERENCE);

        assertThat(content.contentType()).isEqualTo("image/png");
        assertThat(content.bytes()).containsExactly(1, 2, 3);
        verify(draftService, never()).getDraft(any(), anyString());
    }

    @Test
    void providerSubmissionMediaContentRejectsNonOwnerWithForbiddenAccess() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntityWithMedia("UNDER_REVIEW");
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.providerSubmissionMediaContent(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, SUBMISSION_REFERENCE, LOGO_REFERENCE))
                .isInstanceOf(com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException.class);
    }

    @Test
    void missingImmutableMediaObjectReturnsNotFoundWithoutDraftFallback() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntityWithMedia("SUBMITTED");
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(storageService.getObjectBytes("review-logo-key")).thenThrow(new IllegalStateException("missing"));

        assertThatThrownBy(() -> service.submissionMediaContent(SUBMISSION_REFERENCE, LOGO_REFERENCE))
                .isInstanceOf(com.deepthoughtnet.clinic.platform.core.errors.NotFoundException.class);
    }

    @Test
    void publishCreatesVisiblePublicationWithoutMutatingOwnershipOrConsent() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.empty());

        AtomicReference<DiscoverPublicProfilePublicationEntity> saved = new AtomicReference<>();
        when(publications.save(any())).thenAnswer(invocation -> {
            DiscoverPublicProfilePublicationEntity publication = invocation.getArgument(0);
            saved.set(publication);
            return publication;
        });
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        doReturn(new PublicProviderProfileModels.PublicProviderPublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ProviderType.CLINIC,
                "green-valley-family-clinic",
                4,
                NOW,
                "/discover/clinics/green-valley-family-clinic"
        )).when(publicProfileService).upsertLifecycleProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());

        var publication = service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Publish the profile");

        assertThat(publication.publicationStatus()).isEqualTo("PUBLISHED");
        assertThat(publication.effectiveVisibility()).isEqualTo("VISIBLE");
        assertThat(publication.publicPath()).isEqualTo("/discover/clinics/green-valley-family-clinic");
        assertThat(entity.getPublicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(entity.getPublishedAt()).isEqualTo(saved.get().getPublishedAt());
        assertThat(saved.get().getPublishedBy()).isEqualTo(REVIEWER_ID.toString());
        ArgumentCaptor<PublicProviderProfileModels.PublicProviderProfileSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(PublicProviderProfileModels.PublicProviderProfileSnapshot.class);
        verify(publicProfileService).upsertLifecycleProfile(snapshotCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());
        var snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.logoDocumentId()).hasToString(LOGO_REFERENCE);
        assertThat(snapshot.coverImageDocumentId()).hasToString(COVER_REFERENCE);
        assertThat(snapshot.gallery()).hasSize(3);
        assertThat(snapshot.galleryCount()).isEqualTo(3);
        assertThat(snapshot.publishedMedia()).hasSize(5);
        assertThat(snapshot.weeklyTimings()).hasSize(2);
        assertThat(snapshot.timingTimezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void repeatedPublishReconcilesStaleSubmissionWithoutDuplicatePublication() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-1",
                PUBLIC_PROFILE_REFERENCE,
                SUBMISSION_REFERENCE,
                3,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Published previously",
                NOW,
                REVIEWER_ID.toString(),
                NOW,
                NOW
        );
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.of(publication));
        doReturn(new PublicProviderProfileModels.PublicProviderPublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ProviderType.CLINIC,
                "green-valley-family-clinic",
                3,
                NOW,
                "/discover/clinics/green-valley-family-clinic"
        )).when(publicProfileService).upsertLifecycleProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());

        var first = service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Idempotent retry");
        var second = service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Idempotent retry");

        assertThat(first.id()).isEqualTo(publication.getId());
        assertThat(second.id()).isEqualTo(publication.getId());
        assertThat(entity.getPublicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(entity.getPublishedAt()).isEqualTo(NOW);
        verify(submissions, times(1)).save(entity);
        verify(publications, never()).save(any());
    }

    @Test
    void publishedLifecycleDrivesAdminAndProviderDtosAndActions() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 20, NOW);
        DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-20",
                PUBLIC_PROFILE_REFERENCE,
                SUBMISSION_REFERENCE,
                20,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Published version 20",
                NOW,
                REVIEWER_ID.toString(),
                NOW,
                NOW
        );
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(submissions.findAll()).thenReturn(List.of(entity));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.of(publication));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(SUBMISSION_REFERENCE)).thenReturn(List.of());

        var detail = service.findSubmissionByReference(SUBMISSION_REFERENCE).orElseThrow();
        var queue = service.listQueue();

        assertThat(detail.moderationStatus()).isEqualTo("APPROVED");
        assertThat(detail.publicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(detail.allowedActions()).contains("UNPUBLISH_PROFILE").doesNotContain("PUBLISH_PROFILE");
        assertThat(detail.providerAllowedActions()).contains("VIEW_PUBLIC_PROFILE");
        assertThat(queue).singleElement().satisfies(row -> {
            assertThat(row.publicationStatus()).isEqualTo("PUBLISHED");
            assertThat(row.allowedActions()).contains("UNPUBLISH_PROFILE").doesNotContain("PUBLISH_PROFILE");
        });
    }

    @Test
    void startupReconciliationReprojectsCurrentApprovedPublicationIdempotently() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-1",
                PUBLIC_PROFILE_REFERENCE,
                SUBMISSION_REFERENCE,
                3,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Published version 3",
                NOW,
                REVIEWER_ID.toString(),
                NOW,
                NOW
        );
        when(publications.findByCurrentTrueAndPublicationStatusOrderByPublishedAtAsc("PUBLISHED"))
                .thenReturn(List.of(publication));
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        doReturn(new PublicProviderProfileModels.PublicProviderPublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ProviderType.CLINIC,
                "green-valley-family-clinic",
                3,
                NOW,
                "/discover/clinics/green-valley-family-clinic"
        )).when(publicProfileService).upsertLifecycleProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());

        assertThat(service.reconcileCurrentPublishedLifecycles()).isEqualTo(1);
        assertThat(entity.getPublicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(entity.getPublishedAt()).isEqualTo(NOW);
        verify(publicProfileService).upsertLifecycleProfile(any(), eq(3), eq("APPROVED"), eq("PUBLISHED"),
                eq("Published version 3"), eq(NOW), eq("PUBLISHED"), eq("PROVIDER_PUBLIC_PROFILE_DRAFT"),
                eq(PUBLIC_PROFILE_REFERENCE), eq(3L), eq(NOW), eq(0L));
    }

    @Test
    void publishSupersedesOlderCurrentPublicationAndPreservesItsHistory() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", entity.getSubmittedDraftVersion(), NOW);
        DiscoverPublicProfilePublicationEntity olderPublication = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-old",
                PUBLIC_PROFILE_REFERENCE,
                "submission-old",
                7,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Earlier approved submission",
                NOW.minusDays(1),
                NOW.minusDays(1),
                NOW.minusDays(1)
        );
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.of(olderPublication));
        when(publications.saveAndFlush(olderPublication)).thenReturn(olderPublication);
        when(publications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(new PublicProviderProfileModels.PublicProviderPublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ProviderType.CLINIC,
                "green-valley-family-clinic",
                21,
                NOW,
                "/discover/clinics/green-valley-family-clinic"
        )).when(publicProfileService).upsertLifecycleProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());

        var publication = service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Publish approved version");

        assertThat(olderPublication.isCurrent()).isFalse();
        assertThat(olderPublication.getPublicationStatus()).isEqualTo("PUBLISHED");
        assertThat(olderPublication.getApprovedSubmissionReference()).isEqualTo("submission-old");
        assertThat(publication.current()).isTrue();
        assertThat(publication.approvedSubmissionReference()).isEqualTo(SUBMISSION_REFERENCE);
        assertThat(publication.publishedVersion()).isEqualTo(entity.getSubmittedDraftVersion());
        assertThat(publication.slug()).isEqualTo("green-valley-family-clinic");
        verify(publications).saveAndFlush(olderPublication);
    }

    @Test
    void unpublishHidesPublicProjectionButPreservesHistory() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-1",
                PUBLIC_PROFILE_REFERENCE,
                SUBMISSION_REFERENCE,
                3,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Publish the profile",
                NOW,
                NOW,
                NOW
        );
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(publication));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));

        var unpublished = service.unpublish(PUBLIC_PROFILE_REFERENCE, REVIEWER_ID, "Tenant consent disabled");

        assertThat(unpublished.publicationStatus()).isEqualTo("UNPUBLISHED");
        assertThat(unpublished.effectiveVisibility()).isEqualTo("NOT_PUBLISHED");
    }

    private DiscoverPublicProfileSubmissionEntity submittedEntity(String status) {
        DiscoverPublicProfileSubmissionEntity entity = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                SUBMISSION_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.CLINIC,
                DRAFT_REFERENCE,
                3,
                status,
                "UNPUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Green Valley Family Clinic\",\"shortTagline\":\"Family care\",\"description\":\"Trusted care\",\"establishedYear\":\"2022\",\"registrationNumber\":\"PMC/CLINIC/2022/10458\"},\"contact\":{\"publicPhone\":\"+91 98765 02201\",\"publicEmail\":\"contact@greenvalleyclinic.in\",\"website\":\"https://www.greenvalleyclinic.in\",\"whatsappNumber\":\"+91 98765 02201\"},\"services\":{\"items\":[\"General Consultation\"]},\"specialities\":{\"items\":[\"Family Medicine\"]},\"facilities\":{\"items\":[\"Parking\"]},\"languages\":{\"items\":[\"English\"]},\"timings\":{\"timezone\":\"Asia/Kolkata\",\"weekly\":[{\"day\":\"MONDAY\",\"open\":\"09:00\",\"close\":\"13:00\"},{\"day\":\"TUESDAY\",\"open\":\"10:00\",\"close\":\"14:00\"}]},\"media\":{\"logoDocumentId\":\"3d7b60eb-e869-3184-aaea-4a9719fb2cb2\",\"coverDocumentId\":\"527da827-4f73-31db-8298-b31a2688772f\"},\"seo\":{\"slug\":\"green-valley-family-clinic\"}}",
                "{}",
                "{\"logoDocumentId\":\"3d7b60eb-e869-3184-aaea-4a9719fb2cb2\",\"coverDocumentId\":\"527da827-4f73-31db-8298-b31a2688772f\",\"gallery\":[\"d746bbda-491a-32e3-b01b-9af80aec6098\",\"a43459d8-8ff8-3167-8384-3f7d4ae4e6e6\",\"b67e1952-91a4-3d33-bfb8-56b774e12d29\"],\"mediaMetadataByDocumentId\":{\"3d7b60eb-e869-3184-aaea-4a9719fb2cb2\":{\"mediaType\":\"LOGO\",\"storageKey\":\"review-logo-key\",\"contentType\":\"image/png\",\"originalFilename\":\"logo.png\"},\"527da827-4f73-31db-8298-b31a2688772f\":{\"mediaType\":\"COVER\",\"storageKey\":\"review-cover-key\",\"contentType\":\"image/jpeg\",\"originalFilename\":\"cover.jpg\"},\"d746bbda-491a-32e3-b01b-9af80aec6098\":{\"mediaType\":\"GALLERY\",\"storageKey\":\"review-gallery-1\",\"contentType\":\"image/png\",\"originalFilename\":\"gallery-1.png\"},\"a43459d8-8ff8-3167-8384-3f7d4ae4e6e6\":{\"mediaType\":\"GALLERY\",\"storageKey\":\"review-gallery-2\",\"contentType\":\"image/png\",\"originalFilename\":\"gallery-2.png\"},\"b67e1952-91a4-3d33-bfb8-56b774e12d29\":{\"mediaType\":\"GALLERY\",\"storageKey\":\"review-gallery-3\",\"contentType\":\"image/png\",\"originalFilename\":\"gallery-3.png\"}}}",
                PROVIDER_ACCOUNT_ID,
                NOW,
                NOW,
                NOW
        );
        entity.markSubmitted(PROVIDER_ACCOUNT_ID, NOW, NOW);
        return switch (status) {
            case "SUBMITTED" -> entity;
            case "UNDER_REVIEW" -> {
                entity.startReview(REVIEWER_ID, NOW, NOW);
                yield entity;
            }
            default -> entity;
        };
    }

    private DiscoverPublicProfileSubmissionEntity submittedEntityWithMedia(String status) {
        return submittedEntity(status);
    }

    private PublicProfileDraftWorkspaceRecord verifiedDraft() {
        return new PublicProfileDraftWorkspaceRecord(
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                DRAFT_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.CLINIC,
                PROVIDER_ACCOUNT_ID,
                "VERIFIED",
                "ENABLED",
                "UNPUBLISHED",
                "READY_FOR_REVIEW",
                "READY",
                100,
                4,
                NOW,
                NOW,
                NOW,
                NOW,
                "Green Valley Family Clinic",
                "green-valley-family-clinic",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "+91 98765 02201",
                "contact@greenvalleyclinic.in",
                "https://www.greenvalleyclinic.in",
                "+91 98765 02201",
                "PMC/CLINIC/2022/10458",
                2022,
                "HEALTHCARE_CLINIC_PROFILE",
                PUBLIC_PROFILE_REFERENCE,
                1L,
                NOW,
                "/discover/clinics/green-valley-family-clinic",
                List.of("EDIT_PUBLIC_PROFILE", "VIEW_PREVIEW", "SUBMIT_FOR_REVIEW"),
                List.<PublicProfileDraftSectionRecord>of(),
                new PublicProfileDraftReadinessRecord(
                        "READY",
                        true,
                        100,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        NOW,
                        4
                ),
                List.of(),
                Map.<String, PublicProfileDraftFieldSourceRecord>of()
        );
    }

    private com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord verifiedOwnership() {
        return new com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                PUBLIC_PROFILE_REFERENCE,
                com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType.CLINIC,
                PROVIDER_ACCOUNT_ID,
                com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus.VERIFIED,
                "HEALTHCARE_INITIATED_CONNECTION",
                "tenant-1",
                1L,
                NOW,
                null,
                null,
                null,
                true,
                "Verified ownership",
                "{}",
                NOW,
                NOW
        );
    }

    private com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord activeOwnerMembership() {
        return new com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                PUBLIC_PROFILE_REFERENCE,
                PROVIDER_ACCOUNT_ID,
                com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole.OWNER,
                "ACTIVE",
                1L,
                "Owner membership",
                NOW,
                NOW
        );
    }

    private PublicProfileDraftWorkspaceRecord timedDraft() {
        return timedDraftWithTimings("09:00", "13:00");
    }

    private PublicProfileDraftWorkspaceRecord timedDraftWithUpdatedHours() {
        return timedDraftWithTimings("10:00", "14:00");
    }

    private PublicProfileDraftWorkspaceRecord timedDraftWithTimings(String mondayStart, String mondayEnd) {
        return new PublicProfileDraftWorkspaceRecord(
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                DRAFT_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.CLINIC,
                PROVIDER_ACCOUNT_ID,
                "VERIFIED",
                "ENABLED",
                "UNPUBLISHED",
                "READY_FOR_REVIEW",
                "READY",
                100,
                4,
                NOW,
                NOW,
                NOW,
                NOW,
                "Green Valley Family Clinic",
                "green-valley-family-clinic",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "+91 98765 02201",
                "contact@greenvalleyclinic.in",
                "https://www.greenvalleyclinic.in",
                "+91 98765 02201",
                "PMC/CLINIC/2022/10458",
                2022,
                "HEALTHCARE_CLINIC_PROFILE",
                PUBLIC_PROFILE_REFERENCE,
                1L,
                NOW,
                "/discover/clinics/green-valley-family-clinic",
                List.of("EDIT_PUBLIC_PROFILE", "VIEW_PREVIEW", "SUBMIT_FOR_REVIEW"),
                List.of(
                        new PublicProfileDraftSectionRecord(
                                "timings",
                                "Timings",
                                Map.of(
                                        "timezone", "Asia/Kolkata",
                                        "intervals", List.of(
                                                Map.of("dayOfWeek", "MONDAY", "startTime", mondayStart, "endTime", mondayEnd),
                                                Map.of("dayOfWeek", "TUESDAY", "startTime", "09:00", "endTime", "13:00"),
                                                Map.of("dayOfWeek", "TUESDAY", "startTime", "16:00", "endTime", "20:00"),
                                                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "09:00", "endTime", "13:00"),
                                                Map.of("dayOfWeek", "WEDNESDAY", "startTime", "16:00", "endTime", "20:00"),
                                                Map.of("dayOfWeek", "THURSDAY", "startTime", "09:00", "endTime", "13:00"),
                                                Map.of("dayOfWeek", "THURSDAY", "startTime", "16:00", "endTime", "20:00"),
                                                Map.of("dayOfWeek", "FRIDAY", "startTime", "09:00", "endTime", "13:00"),
                                                Map.of("dayOfWeek", "FRIDAY", "startTime", "16:00", "endTime", "20:00"),
                                                Map.of("dayOfWeek", "SATURDAY", "startTime", "09:00", "endTime", "14:00")
                                        )
                                ),
                                Map.of()
                        )
                ),
                new PublicProfileDraftReadinessRecord(
                        "READY",
                        true,
                        100,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        NOW,
                        4
                ),
                List.of(),
                Map.<String, PublicProfileDraftFieldSourceRecord>of()
        );
    }

}
