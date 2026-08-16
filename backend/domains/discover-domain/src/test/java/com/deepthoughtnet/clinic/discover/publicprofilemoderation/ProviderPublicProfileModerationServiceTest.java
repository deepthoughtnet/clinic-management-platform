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
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentEntity;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentRepository;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.PublicHospitalDoctorAssociationService;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.PublicHospitalDoctorDraftAssociationService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftFieldSourceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileModerationQueueRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileSubmissionEligibilityRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
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
    private static final String HOSPITAL_LONG_TAGLINE =
            "Jeevanam Multispeciality Hospital is a modern tertiary-care hospital delivering comprehensive outpatient, inpatient, emergency, surgical, maternity, diagnostic and critical care services. "
                    + "Our multidisciplinary medical team provides evidence-based treatment supported by advanced diagnostics, 24×7 emergency services, intensive care units and patient-centred healthcare.";
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-04T10:15:30Z");

    @Mock
    private ProviderPublicProfileDraftService draftService;
    @Mock
    private ProviderOwnershipService ownershipService;
    @Mock
    private ProviderPublicProfileService publicProfileService;
    @Mock
    private PublicHospitalDoctorDraftAssociationService hospitalDoctorDraftAssociationService;
    @Mock
    private PublicHospitalDoctorAssociationService hospitalDoctorAssociationService;
    @Mock
    private ProviderDocumentRepository documents;
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
                hospitalDoctorDraftAssociationService,
                hospitalDoctorAssociationService,
                documents,
                submissions,
                findings,
                publications,
                storageService,
                new ObjectMapper()
        );
        lenient().when(submissions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(findings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(publications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(hospitalDoctorDraftAssociationService.listDraftDoctorReferencesByHospital(any())).thenReturn(List.of());
        lenient().when(hospitalDoctorAssociationService.reconcileHospitalDoctors(any(), any(), any(), any())).thenReturn(0);
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
    void underReviewSubmissionExposesModerationActionsEvenWhenPublicationIsAlreadyLive() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.of(DiscoverPublicProfilePublicationEntity.create(
                        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                        "publication-1",
                        PUBLIC_PROFILE_REFERENCE,
                        SUBMISSION_REFERENCE,
                        2,
                        "PUBLISHED",
                        "green-valley-family-clinic",
                        "/discover/clinics/green-valley-family-clinic",
                        "Published version 2",
                        NOW,
                        REVIEWER_ID.toString(),
                        NOW,
                        NOW
                )));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(anyString())).thenReturn(List.of());

        var record = service.findSubmissionByReference(SUBMISSION_REFERENCE).orElseThrow();

        assertThat(record.moderationStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(record.publicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(record.allowedActions()).contains("APPROVE_SUBMISSION", "REQUEST_CHANGES", "REJECT_SUBMISSION");
        assertThat(record.allowedActions()).contains("VIEW_PUBLIC_PROFILE", "UNPUBLISH_PROFILE", "VIEW_REVIEW_HISTORY");
        assertThat(record.allowedActions()).doesNotContain("PUBLISH_PROFILE");
    }

    @Test
    void publishedSnapshotsExposeCanonicalUnpublishCapabilityAcrossProviderTypes() {
        for (ProviderType providerType : List.of(ProviderType.INDIVIDUAL_DOCTOR, ProviderType.CLINIC, ProviderType.HOSPITAL)) {
            DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW", providerType);
            entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
            entity.markPublished(NOW, NOW);
            when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(entity));
            when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.empty());

            assertThat(service.publicationStatus(PUBLIC_PROFILE_REFERENCE)).isEqualTo("PUBLISHED");
            assertThat(service.canUnpublish(PUBLIC_PROFILE_REFERENCE)).isTrue();
            assertThat(service.publicationAllowedActions(PUBLIC_PROFILE_REFERENCE))
                    .contains("UNPUBLISH_PROFILE", "VIEW_PUBLIC_PROFILE", "VIEW_REVIEW_HISTORY");
        }
    }

    @Test
    void currentPublicationExposesCanonicalUnpublishCapabilityEvenWhenLatestSubmissionIsMissingAcrossProviderTypes() {
        for (ProviderType providerType : List.of(ProviderType.INDIVIDUAL_DOCTOR, ProviderType.CLINIC, ProviderType.HOSPITAL)) {
            DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                    UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    "publication-" + providerType.name(),
                    PUBLIC_PROFILE_REFERENCE,
                    "submission-" + providerType.name(),
                    3,
                    "PUBLISHED",
                    "green-valley-family-clinic",
                    "/discover/clinics/green-valley-family-clinic",
                    "Publish the profile",
                    NOW,
                    REVIEWER_ID.toString(),
                    NOW,
                    NOW
            );
            when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(publication));

            assertThat(service.publicationStatus(PUBLIC_PROFILE_REFERENCE)).isEqualTo("PUBLISHED");
            assertThat(service.canUnpublish(PUBLIC_PROFILE_REFERENCE)).isTrue();
            assertThat(service.publicationAllowedActions(PUBLIC_PROFILE_REFERENCE))
                    .contains("UNPUBLISH_PROFILE", "VIEW_PUBLIC_PROFILE", "VIEW_REVIEW_HISTORY");
        }
    }

    @Test
    void submittedSnapshotContainsPersistedTimings() {
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(timedDraft()));
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
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(timedDraft()));
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
    void submissionMediaContentResolvesStorageKeyFromProviderDocumentWhenSnapshotMetadataOmitsIt() {
        DiscoverPublicProfileSubmissionEntity entity = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                SUBMISSION_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.HOSPITAL,
                DRAFT_REFERENCE,
                3,
                "SUBMITTED",
                "PUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Jeevanam Multispeciality Hospital\"},\"media\":{\"logoDocumentId\":\"c892c8c7-42ec-4292-963d-28b43a8955ef\",\"coverDocumentId\":\"046978b4-7cf4-4830-9a4f-980e397ed219\"}}",
                "{}",
                "{\"logoDocumentId\":\"c892c8c7-42ec-4292-963d-28b43a8955ef\",\"coverDocumentId\":\"046978b4-7cf4-4830-9a4f-980e397ed219\",\"gallery\":[],\"mediaMetadataByDocumentId\":{\"046978b4-7cf4-4830-9a4f-980e397ed219\":{\"mediaType\":\"COVER\",\"contentType\":\"image/jpeg\",\"originalFilename\":\"cover.jpg\"}},\"galleryAltTextByDocumentId\":{}}",
                PROVIDER_ACCOUNT_ID,
                NOW,
                NOW,
                NOW
        );
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        ProviderDocumentEntity document = org.mockito.Mockito.mock(ProviderDocumentEntity.class);
        when(document.getStorageKey()).thenReturn("review-cover-key");
        when(documents.findById(UUID.fromString("046978b4-7cf4-4830-9a4f-980e397ed219"))).thenReturn(Optional.of(document));
        when(storageService.getObjectBytes("review-cover-key")).thenReturn("image-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var content = service.submissionMediaContent(SUBMISSION_REFERENCE, "046978b4-7cf4-4830-9a4f-980e397ed219");

        assertThat(content.mediaReference()).isEqualTo("046978b4-7cf4-4830-9a4f-980e397ed219");
        assertThat(content.originalFilename()).isEqualTo("cover.jpg");
        assertThat(new String(content.bytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("image-bytes");
    }

    @Test
    void submittedSnapshotRemainsImmutableAfterDraftEdit() {
        AtomicReference<PublicProfileDraftWorkspaceRecord> draft = new AtomicReference<>(timedDraft());
        AtomicReference<DiscoverPublicProfileSubmissionEntity> savedSubmission = new AtomicReference<>();
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenAnswer(invocation -> Optional.ofNullable(draft.get()));
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
        when(submissions.findByCurrentTrueAndModerationStatusInOrderBySubmittedAtDescModerationRevisionDesc(List.of("SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED", "APPROVED", "PUBLISHED")))
                .thenReturn(List.of(entity));
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
    void queueFiltersByCanonicalProviderTypeAndText() {
        DiscoverPublicProfileSubmissionEntity clinic = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "submission-clinic",
                "clinic-ref",
                ProviderType.CLINIC,
                "draft-clinic",
                2,
                "SUBMITTED",
                "UNPUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Green Valley Family Clinic\"},\"contact\":{\"city\":\"Pune\",\"area\":\"Wakad\"}}",
                "{}",
                "{\"gallery\":[],\"mediaMetadataByDocumentId\":{}}",
                PROVIDER_ACCOUNT_ID,
                NOW,
                NOW,
                NOW
        );
        DiscoverPublicProfileSubmissionEntity hospital = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "submission-hospital",
                "hospital-ref",
                ProviderType.HOSPITAL,
                "draft-hospital",
                3,
                "UNDER_REVIEW",
                "PUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Jeevanam Multispeciality Hospital\"},\"contact\":{\"city\":\"Pune\",\"area\":\"Pune\"}}",
                "{}",
                "{\"gallery\":[],\"mediaMetadataByDocumentId\":{}}",
                PROVIDER_ACCOUNT_ID,
                NOW,
                NOW,
                NOW
        );
        DiscoverPublicProfileSubmissionEntity doctor = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "submission-doctor",
                "doctor-ref",
                ProviderType.INDIVIDUAL_DOCTOR,
                "draft-doctor",
                4,
                "CHANGES_REQUESTED",
                "UNPUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Amit Verma\"},\"contact\":{\"city\":\"Pune\",\"area\":\"Aundh\"}}",
                "{}",
                "{\"gallery\":[],\"mediaMetadataByDocumentId\":{}}",
                PROVIDER_ACCOUNT_ID,
                NOW,
                NOW,
                NOW
        );
        when(submissions.findByCurrentTrueAndModerationStatusInOrderBySubmittedAtDescModerationRevisionDesc(List.of("SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED", "APPROVED", "PUBLISHED")))
                .thenReturn(List.of(clinic, hospital, doctor));

        assertThat(service.listQueue()).hasSize(3);
        assertThat(service.listQueue("CLINIC", null, null)).extracting(PublicProfileModerationQueueRecord::publicProfileType).containsExactly(ProviderType.CLINIC);
        assertThat(service.listQueue("HOSPITAL", "Jeevanam", "Pune")).extracting(PublicProfileModerationQueueRecord::publicProfileType).containsExactly(ProviderType.HOSPITAL);
        assertThat(service.listQueue("DOCTOR", "Amit", "Pune")).extracting(PublicProfileModerationQueueRecord::publicProfileType).containsExactly(ProviderType.INDIVIDUAL_DOCTOR);
    }

    @Test
    void submissionEligibilityAllowsResubmissionWhenDraftDiffersFromRequestedChangesSnapshot() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.requestChanges(REVIEWER_ID, NOW, "Please revise the timings.", NOW);
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(timedDraft()));
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(entity));

        PublicProfileSubmissionEligibilityRecord eligibility = service.submissionEligibility(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);

        assertThat(eligibility.submissionEligible()).isTrue();
        assertThat(eligibility.submissionBlockers()).doesNotContain("RESUBMISSION_REQUIRED", "ACTIVE_SUBMISSION_EXISTS");
        assertThat(eligibility.allowedActions()).contains("SUBMIT_FOR_REVIEW");
        assertThat(eligibility.moderationStatus()).isEqualTo("CHANGES_REQUESTED");
    }

    @Test
    void submissionEligibilityReportsLivePublicationAndReadOnlyStateDuringActiveReview() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-locked",
                PUBLIC_PROFILE_REFERENCE,
                SUBMISSION_REFERENCE,
                3,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Published hospital snapshot",
                NOW,
                REVIEWER_ID.toString(),
                NOW,
                NOW
        );
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(timedDraft()));
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(entity));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(publication));

        PublicProfileSubmissionEligibilityRecord eligibility = service.submissionEligibility(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);

        assertThat(eligibility.editable()).isFalse();
        assertThat(eligibility.publicationStatus()).isEqualTo("PUBLISHED");
        assertThat(eligibility.publicUrl()).isEqualTo("/discover/clinics/green-valley-family-clinic");
        assertThat(eligibility.submittedDraftVersion()).isEqualTo(3);
        assertThat(eligibility.moderationStatus()).isEqualTo("UNDER_REVIEW");
        verify(draftService, never()).getDraft(any(), anyString());
    }

    @Test
    void publishedSubmissionDoesNotCountAsAnActiveReview() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntity("PUBLISHED");
        entity.approve(REVIEWER_ID, NOW, "Approved and published", 3, NOW);
        entity.markPublished(NOW, NOW);
        DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-locked",
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
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(timedDraft()));
        when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(entity));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(publication));

        PublicProfileSubmissionEligibilityRecord eligibility = service.submissionEligibility(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);

        assertThat(eligibility.submissionEligible()).isTrue();
        assertThat(eligibility.submissionBlockers()).doesNotContain("ACTIVE_SUBMISSION_EXISTS");
        assertThat(eligibility.moderationStatus()).isEqualTo("APPROVED");
        assertThat(eligibility.publicationStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void publishedSubmissionDoesNotCountAsAnActiveReviewAcrossProviderTypes() {
        for (ProviderType providerType : List.of(ProviderType.HOSPITAL, ProviderType.CLINIC)) {
            DiscoverPublicProfileSubmissionEntity entity = submittedEntity("PUBLISHED", providerType);
            entity.approve(REVIEWER_ID, NOW, "Approved and published", 3, NOW);
            entity.markPublished(NOW, NOW);
            DiscoverPublicProfilePublicationEntity publication = DiscoverPublicProfilePublicationEntity.create(
                    UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                    "publication-locked-" + providerType.name().toLowerCase(),
                    PUBLIC_PROFILE_REFERENCE,
                    SUBMISSION_REFERENCE,
                    3,
                    "PUBLISHED",
                    providerType == ProviderType.HOSPITAL
                            ? "jeevanam-multispeciality-hospital"
                            : "green-valley-family-clinic",
                    providerType == ProviderType.HOSPITAL
                            ? "/discover/hospitals/jeevanam-multispeciality-hospital"
                            : "/discover/clinics/green-valley-family-clinic",
                    "Published version 3",
                    NOW,
                    REVIEWER_ID.toString(),
                    NOW,
                    NOW
            );
            when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership(providerType)));
            when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(timedDraft(providerType)));
            when(submissions.findByPublicProfileReferenceOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(entity));
            when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(publication));

            PublicProfileSubmissionEligibilityRecord eligibility = service.submissionEligibility(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE, true);

            assertThat(eligibility.submissionEligible()).isTrue();
            assertThat(eligibility.submissionBlockers()).doesNotContain("ACTIVE_SUBMISSION_EXISTS");
            assertThat(eligibility.moderationStatus()).isEqualTo("APPROVED");
            assertThat(eligibility.publicationStatus()).isEqualTo("PUBLISHED");
        }
    }

    @Test
    void submitForReviewCreatesNewSubmissionAfterRequestedChangesAreAddressed() {
        DiscoverPublicProfileSubmissionEntity existing = submittedEntity("UNDER_REVIEW");
        existing.requestChanges(REVIEWER_ID, NOW, "Please revise the timings.", NOW);
        AtomicReference<DiscoverPublicProfileSubmissionEntity> savedSubmission = new AtomicReference<>();
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedOwnership()));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(timedDraft()));
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
    void submissionMediaContentFallsBackToProviderDocumentForLogoAndCoverWhenSnapshotMetadataIsMissing() {
        DiscoverPublicProfileSubmissionEntity entity = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("eeeeeeee-dddd-dddd-dddd-dddddddddddd"),
                SUBMISSION_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.HOSPITAL,
                DRAFT_REFERENCE,
                3,
                "UNDER_REVIEW",
                "PUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Jeevanam Multispeciality Hospital\"},\"media\":{\"logoDocumentId\":\"c892c8c7-42ec-4292-963d-28b43a8955ef\",\"coverDocumentId\":\"046978b4-7cf4-4830-9a4f-980e397ed219\"}}",
                "{}",
                "{\"gallery\":[],\"mediaMetadataByDocumentId\":{}}",
                PROVIDER_ACCOUNT_ID,
                NOW,
                NOW,
                NOW
        );
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        ProviderDocumentEntity logo = org.mockito.Mockito.mock(ProviderDocumentEntity.class);
        when(logo.getStorageKey()).thenReturn("review-logo-key");
        when(logo.getContentType()).thenReturn("image/png");
        when(logo.getOriginalFilename()).thenReturn("logo.png");
        ProviderDocumentEntity cover = org.mockito.Mockito.mock(ProviderDocumentEntity.class);
        when(cover.getStorageKey()).thenReturn("review-cover-key");
        when(cover.getContentType()).thenReturn("image/jpeg");
        when(cover.getOriginalFilename()).thenReturn("cover.jpg");
        when(documents.findById(UUID.fromString("c892c8c7-42ec-4292-963d-28b43a8955ef"))).thenReturn(Optional.of(logo));
        when(documents.findById(UUID.fromString("046978b4-7cf4-4830-9a4f-980e397ed219"))).thenReturn(Optional.of(cover));
        when(storageService.getObjectBytes("review-logo-key")).thenReturn("logo-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(storageService.getObjectBytes("review-cover-key")).thenReturn("cover-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var logoContent = service.submissionMediaContent(SUBMISSION_REFERENCE, "c892c8c7-42ec-4292-963d-28b43a8955ef");
        var coverContent = service.submissionMediaContent(SUBMISSION_REFERENCE, "046978b4-7cf4-4830-9a4f-980e397ed219");

        assertThat(logoContent.originalFilename()).isEqualTo("logo.png");
        assertThat(logoContent.contentType()).isEqualTo("image/png");
        assertThat(new String(logoContent.bytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("logo-bytes");
        assertThat(coverContent.originalFilename()).isEqualTo("cover.jpg");
        assertThat(coverContent.contentType()).isEqualTo("image/jpeg");
        assertThat(new String(coverContent.bytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("cover-bytes");
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
        when(submissions.findByCurrentTrueAndModerationStatusInOrderBySubmittedAtDescModerationRevisionDesc(List.of("SUBMITTED", "UNDER_REVIEW", "CHANGES_REQUESTED", "APPROVED", "PUBLISHED")))
                .thenReturn(List.of(entity));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.of(publication));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(findings.findBySubmissionReferenceOrderByCreatedAtAsc(SUBMISSION_REFERENCE)).thenReturn(List.of());

        var detail = service.findSubmissionByReference(SUBMISSION_REFERENCE).orElseThrow();
        var queue = service.listQueue();

        assertThat(detail.moderationStatus()).isEqualTo("APPROVED");
        assertThat(detail.publicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(detail.allowedActions()).contains("PUBLISH_PROFILE", "UNPUBLISH_PROFILE");
        assertThat(detail.providerAllowedActions()).contains("VIEW_PUBLIC_PROFILE");
        assertThat(queue).singleElement().satisfies(row -> {
            assertThat(row.publicationStatus()).isEqualTo("PUBLISHED");
            assertThat(row.allowedActions()).contains("PUBLISH_PROFILE", "UNPUBLISH_PROFILE");
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
    void publishHospitalProfileSyncsHospitalDoctorsFromDraftProjection() {
        DiscoverPublicProfileSubmissionEntity entity = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                SUBMISSION_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.HOSPITAL,
                DRAFT_REFERENCE,
                3,
                "UNDER_REVIEW",
                "UNPUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Jeevanam Multispeciality Hospital\",\"shortTagline\":\"Care\",\"description\":\"Trusted hospital for families.\",\"establishedYear\":\"2018\"},\"contact\":{\"publicPhone\":\"+91 98765 01502\",\"publicEmail\":\"contact@jeevanamhospital.in\",\"website\":\"https://www.jeevanamhospital.in\",\"whatsappNumber\":\"+91 98765 01502\"},\"services\":{\"items\":[\"General Medicine\"]},\"specialities\":{\"items\":[\"General Medicine\"]},\"facilities\":{\"items\":[\"Emergency\"]},\"languages\":{\"items\":[\"English\"]},\"timings\":{\"timezone\":\"Asia/Kolkata\",\"weekly\":[{\"day\":\"MONDAY\",\"open\":\"00:00\",\"close\":\"23:59\"}]},\"media\":{\"logoDocumentId\":\"3d7b60eb-e869-3184-aaea-4a9719fb2cb2\",\"coverDocumentId\":\"527da827-4f73-31db-8298-b31a2688772f\"},\"seo\":{\"slug\":\"jeevanam-multispeciality-hospital\"}}",
                "{}",
                "{\"logoDocumentId\":\"3d7b60eb-e869-3184-aaea-4a9719fb2cb2\",\"coverDocumentId\":\"527da827-4f73-31db-8298-b31a2688772f\",\"gallery\":[],\"mediaMetadataByDocumentId\":{}}",
                PROVIDER_ACCOUNT_ID,
                NOW,
                NOW,
                NOW
        );
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(hospitalDoctorDraftAssociationService.listDraftDoctorReferencesByHospital(UUID.fromString(PUBLIC_PROFILE_REFERENCE)))
                .thenReturn(List.of(UUID.fromString("23cf0f04-3152-46ef-a0f6-3b243f90bbc5"), UUID.fromString("a57d88d7-afac-443d-8a03-9f88e2155df6")));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.empty());
        doReturn(new PublicProviderProfileModels.PublicProviderPublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ProviderType.HOSPITAL,
                "jeevanam-multispeciality-hospital",
                4,
                NOW,
                "/discover/hospitals/jeevanam-multispeciality-hospital"
        )).when(publicProfileService).upsertLifecycleProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());

        service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Publish the hospital profile");

        verify(hospitalDoctorAssociationService).reconcileHospitalDoctors(
                eq(UUID.fromString(PUBLIC_PROFILE_REFERENCE)),
                eq(UUID.fromString(PUBLIC_PROFILE_REFERENCE)),
                eq(List.of(UUID.fromString("23cf0f04-3152-46ef-a0f6-3b243f90bbc5"), UUID.fromString("a57d88d7-afac-443d-8a03-9f88e2155df6"))),
                any()
        );
    }

    @Test
    void publishHospitalProfileCarriesLongSubmittedTaglineIntoImmutableSnapshot() {
        DiscoverPublicProfileSubmissionEntity entity = submittedHospitalEntity("UNDER_REVIEW", HOSPITAL_LONG_TAGLINE);
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.empty());
        doReturn(new PublicProviderProfileModels.PublicProviderPublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ProviderType.HOSPITAL,
                "jeevanam-multispeciality-hospital",
                4,
                NOW,
                "/discover/hospitals/jeevanam-multispeciality-hospital"
        )).when(publicProfileService).upsertLifecycleProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());

        service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Publish the hospital profile");

        ArgumentCaptor<PublicProviderProfileModels.PublicProviderProfileSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(PublicProviderProfileModels.PublicProviderProfileSnapshot.class);
        verify(publicProfileService).upsertLifecycleProfile(snapshotCaptor.capture(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());
        var snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.tagline()).isEqualTo(HOSPITAL_LONG_TAGLINE);
        assertThat(snapshot.tagline()).hasSize(369);
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

    @Test
    void republishAfterUnpublishReusesApprovedSnapshotWhenDraftIsUnchanged() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntityForRepublish("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        DiscoverPublicProfilePublicationEntity unpublished = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-1",
                PUBLIC_PROFILE_REFERENCE,
                SUBMISSION_REFERENCE,
                3,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Publish the profile",
                NOW.minusHours(1),
                REVIEWER_ID.toString(),
                NOW.minusHours(1),
                NOW.minusHours(1)
        );
        unpublished.unpublish("Unpublished for review", REVIEWER_ID.toString(), NOW, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(publications.findByPublicProfileReferenceOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(List.of(unpublished));
        when(publications.findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.empty());
        when(publications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doReturn(new PublicProviderProfileModels.PublicProviderPublicationRecord(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                ProviderType.CLINIC,
                "green-valley-family-clinic",
                3,
                NOW,
                "/discover/clinics/green-valley-family-clinic"
        )).when(publicProfileService).upsertLifecycleProfile(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyLong(), any(), anyLong());
        var republished = service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Republish approved snapshot");

        assertThat(republished.publicationStatus()).isEqualTo("PUBLISHED");
        assertThat(republished.current()).isTrue();
        verify(publicProfileService).upsertLifecycleProfile(any(), eq(3), eq("APPROVED"), eq("PUBLISHED"),
                eq("Republish approved snapshot"), any(), eq("PUBLISHED"), eq("PROVIDER_PUBLIC_PROFILE_DRAFT"),
                eq(PUBLIC_PROFILE_REFERENCE), eq(3L), eq(NOW), eq(0L));
    }

    @Test
    void republishAfterUnpublishRequiresReviewWhenDraftChanged() {
        DiscoverPublicProfileSubmissionEntity entity = submittedEntityForRepublish("UNDER_REVIEW");
        entity.startReview(REVIEWER_ID, NOW, NOW);
        entity.approve(REVIEWER_ID, NOW, "Looks good", 3, NOW);
        DiscoverPublicProfilePublicationEntity unpublished = DiscoverPublicProfilePublicationEntity.create(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                "publication-1",
                PUBLIC_PROFILE_REFERENCE,
                SUBMISSION_REFERENCE,
                3,
                "PUBLISHED",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Publish the profile",
                NOW.minusHours(1),
                REVIEWER_ID.toString(),
                NOW.minusHours(1),
                NOW.minusHours(1)
        );
        unpublished.unpublish("Unpublished for review", REVIEWER_ID.toString(), NOW, NOW);
        when(submissions.findBySubmissionReference(SUBMISSION_REFERENCE)).thenReturn(Optional.of(entity));
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(republishReadyDraft("Green Valley Family Clinic Updated")));
        when(publications.findByPublicProfileReferenceOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(List.of(unpublished));

        assertThatThrownBy(() -> service.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Republish changed snapshot"))
                .isInstanceOf(ProviderOwnershipConflictException.class)
                .satisfies(throwable -> assertThat(((ProviderOwnershipConflictException) throwable).getCode()).isEqualTo("republish_requires_review"));
    }

    private DiscoverPublicProfileSubmissionEntity submittedEntity(String status) {
        return submittedEntity(status, ProviderType.CLINIC);
    }

    private DiscoverPublicProfileSubmissionEntity submittedEntity(String status, ProviderType providerType) {
        DiscoverPublicProfileSubmissionEntity entity = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                SUBMISSION_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                providerType,
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

    private DiscoverPublicProfileSubmissionEntity submittedEntityForRepublish(String status) {
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
                "{}",
                "{}",
                "{}",
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

    private DiscoverPublicProfileSubmissionEntity submittedHospitalEntity(String status, String shortTagline) {
        DiscoverPublicProfileSubmissionEntity entity = DiscoverPublicProfileSubmissionEntity.create(
                UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                SUBMISSION_REFERENCE,
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.HOSPITAL,
                DRAFT_REFERENCE,
                3,
                status,
                "UNPUBLISHED",
                "ENABLED",
                "{}",
                "{}",
                "{\"about\":{\"displayName\":\"Jeevanam Multispeciality Hospital\",\"shortTagline\":\""
                        + shortTagline
                        + "\",\"description\":\"Trusted hospital for families.\",\"establishedYear\":\"2018\"},\"contact\":{\"publicPhone\":\"+91 98765 01502\",\"publicEmail\":\"contact@jeevanamhospital.in\",\"website\":\"https://www.jeevanamhospital.in\",\"whatsappNumber\":\"+91 98765 01502\"},\"services\":{\"items\":[\"General Medicine\"]},\"specialities\":{\"items\":[\"General Medicine\"]},\"facilities\":{\"items\":[\"Emergency\"]},\"languages\":{\"items\":[\"English\"]},\"timings\":{\"timezone\":\"Asia/Kolkata\",\"weekly\":[{\"day\":\"MONDAY\",\"open\":\"00:00\",\"close\":\"23:59\"}]},\"media\":{\"logoDocumentId\":\"3d7b60eb-e869-3184-aaea-4a9719fb2cb2\",\"coverDocumentId\":\"527da827-4f73-31db-8298-b31a2688772f\"},\"seo\":{\"slug\":\"jeevanam-multispeciality-hospital\"}}",
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

    private PublicProfileDraftWorkspaceRecord timedDraft(ProviderType providerType) {
        return switch (providerType) {
            case HOSPITAL -> new PublicProfileDraftWorkspaceRecord(
                    UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                    DRAFT_REFERENCE,
                    PUBLIC_PROFILE_REFERENCE,
                    providerType,
                    PROVIDER_ACCOUNT_ID,
                    "VERIFIED",
                    "ENABLED",
                    "PUBLISHED",
                    "READY_FOR_REVIEW",
                    "READY",
                    100,
                    4,
                    NOW,
                    NOW,
                    NOW,
                    NOW,
                    "Jeevanam Multispeciality Hospital",
                    "jeevanam-multispeciality-hospital",
                    "Pune",
                    "Wakad",
                    "Maharashtra",
                    "India",
                    "+91 98765 01502",
                    "contact@jeevanamhospital.in",
                    "https://www.jeevanamhospital.in",
                    "+91 98765 01502",
                    "PMC/HOSPITAL/2018/00001",
                    2018,
                    "HEALTHCARE_HOSPITAL_PROFILE",
                    PUBLIC_PROFILE_REFERENCE,
                    3L,
                    NOW,
                    "/discover/hospitals/jeevanam-multispeciality-hospital",
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
            default -> timedDraft();
        };
    }

    private com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord verifiedOwnership(ProviderType providerType) {
        return new com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                PUBLIC_PROFILE_REFERENCE,
                switch (providerType) {
                    case HOSPITAL -> com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType.HOSPITAL;
                    default -> com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType.CLINIC;
                },
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

    private PublicProfileDraftWorkspaceRecord verifiedDraft() {
        return verifiedDraftWithDisplayName("Green Valley Family Clinic");
    }

    private PublicProfileDraftWorkspaceRecord verifiedDraftWithDisplayName(String displayName) {
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
                displayName,
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

    private PublicProfileDraftWorkspaceRecord republishReadyDraft(String displayName) {
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
                displayName,
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
                                "about",
                                "About",
                                Map.of(
                                        "displayName", displayName,
                                        "shortTagline", "Family care"
                                ),
                                Map.<String, PublicProfileDraftFieldSourceRecord>of()
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
