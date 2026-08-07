package com.deepthoughtnet.clinic.api.discover.publicprofilemoderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.ApiBffApplication;
import com.deepthoughtnet.clinic.api.platform.discover.HealthcarePublicListingStartupReconciler;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftFieldSourceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftReadinessRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionEntity;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest(
        classes = ApiBffApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.lazy-initialization=true",
                "clinic.ai.enabled=false",
                "clinic.ocr.enabled=false",
                "clinic.notifications.scheduler.enabled=false",
                "clinic.notifications.dispatcher.enabled=false",
                "clinic.carepilot.scheduler.enabled=false",
                "clinic.keycloak.admin.enabled=false"
        }
)
class ProviderPublicProfileModerationPublishIntegrationTest {
    private static final String PUBLIC_PROFILE_REFERENCE = "407dbc68-107d-4f64-83c8-6499e50e5c78";
    private static final String SUBMISSION_REFERENCE = "a01bc24b-3c5c-4038-8feb-02dd6f8de43a";
    private static final UUID REVIEWER_ID = UUID.fromString("07760233-0e3e-4e2f-8732-68543c23a7ed");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-06T04:22:14.4331Z");

    @Autowired
    private ProviderPublicProfileModerationService moderationService;
    @Autowired
    private DiscoverPublicProfileSubmissionRepository submissions;
    @Autowired
    private DiscoverPublicProfilePublicationRepository publications;
    @Autowired
    private DiscoverPublicProviderProfileRepository profiles;
    @Autowired
    private DiscoverPublicProviderProfileVersionRepository versions;

    @MockBean
    private ProviderPublicProfileDraftService draftService;
    @MockBean
    private HealthcarePublicListingStartupReconciler healthcarePublicListingStartupReconciler;
    @MockBean
    private ObjectStorageService objectStorageService;
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void publishAdoptsHistoricalSlugOwnerAndPersistsPublication() {
        when(draftService.findDraft(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(verifiedDraft()));
        when(objectStorageService.generatePresignedDownloadUrl(anyString(), any(Duration.class)))
                .thenAnswer(invocation -> "https://storage.test/" + invocation.getArgument(0, String.class));

        DiscoverPublicProfileSubmissionEntity before = submissions.findBySubmissionReference(SUBMISSION_REFERENCE).orElseThrow();
        assertThat(before.getModerationStatus()).isEqualTo("APPROVED");
        assertThat(before.getApprovedVersionNumber()).isEqualTo(20);
        String immutableContentBefore = before.getContentSnapshotJson();
        long publicationCountBefore = publications.findByPublicProfileReferenceOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE).size();
        long profileCountBefore = profiles.count();
        DiscoverPublicProviderProfileEntity profileBefore = profiles.findByCanonicalSlug("green-valley-family-clinic").orElseThrow();
        long projectionVersionCountBefore = versions.findByProviderIdOrderByVersionNumberDesc(profileBefore.getProviderId()).size();

        var publication = moderationService.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Publish approved version");
        long projectionVersionCountAfterFirstPublish = versions
                .findByProviderIdOrderByVersionNumberDesc(profileBefore.getProviderId()).size();
        var repeatedPublication = moderationService.publish(SUBMISSION_REFERENCE, REVIEWER_ID, "Publish approved version again");

        assertThat(publication.publicationStatus()).isEqualTo("PUBLISHED");
        assertThat(publication.publicProfileReference()).isEqualTo(PUBLIC_PROFILE_REFERENCE);
        assertThat(publication.approvedSubmissionReference()).isEqualTo(SUBMISSION_REFERENCE);
        assertThat(publication.publicPath()).isEqualTo("/discover/clinics/green-valley-family-clinic");
        assertThat(repeatedPublication.id()).isEqualTo(publication.id());

        DiscoverPublicProfilePublicationEntity currentPublication = publications
                .findFirstByPublicProfileReferenceAndCurrentTrueOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)
                .orElseThrow();
        assertThat(currentPublication.getPublicationStatus()).isEqualTo("PUBLISHED");
        assertThat(currentPublication.getApprovedSubmissionReference()).isEqualTo(SUBMISSION_REFERENCE);
        assertThat(currentPublication.getSlug()).isEqualTo("green-valley-family-clinic");
        assertThat(currentPublication.getPublicPath()).isEqualTo("/discover/clinics/green-valley-family-clinic");
        assertThat(currentPublication.getPublishedBy()).isNotBlank();

        DiscoverPublicProfileSubmissionEntity after = submissions.findBySubmissionReference(SUBMISSION_REFERENCE).orElseThrow();
        assertThat(after.getModerationStatus()).isEqualTo("APPROVED");
        assertThat(after.getApprovedVersionNumber()).isEqualTo(20);
        assertThat(after.getPublicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(after.getPublishedAt()).isEqualTo(currentPublication.getPublishedAt());
        assertThat(after.getContentSnapshotJson()).isEqualTo(immutableContentBefore);

        var review = moderationService.findSubmissionByReference(SUBMISSION_REFERENCE).orElseThrow();
        assertThat(review.publicationStatusSnapshot()).isEqualTo("PUBLISHED");
        assertThat(review.allowedActions()).contains("UNPUBLISH_PROFILE").doesNotContain("PUBLISH_PROFILE");
        assertThat(review.providerAllowedActions()).contains("VIEW_PUBLIC_PROFILE");

        DiscoverPublicProviderProfileEntity profile = profiles.findByCanonicalSlug("green-valley-family-clinic").orElseThrow();
        assertThat(profile.getPublicationStatus()).isEqualTo("PUBLISHED");
        assertThat(profile.getCanonicalSlug()).isEqualTo("green-valley-family-clinic");
        assertThat(profile.getLatestPublishedVersionNumber()).isGreaterThanOrEqualTo(20);
        assertThat(versions.findByProviderIdAndVersionNumber(profile.getProviderId(), 20)).isPresent();
        var approvedProjection = versions.findById(profile.getLatestPublishedVersionId()).orElseThrow();
        assertThat(approvedProjection.getSourceSubmissionVersionNumber()).isEqualTo(20);
        assertThat(profile.getLatestPublishedVersionId()).isEqualTo(approvedProjection.getId());
        assertThat(approvedProjection.getSnapshotJson()).contains("publishedMedia", "weeklyTimings", "timingTimezone");
        assertThat(publications.findByPublicProfileReferenceOrderByPublishedAtDesc(PUBLIC_PROFILE_REFERENCE)).hasSize((int) publicationCountBefore);
        assertThat(profiles.count()).isEqualTo(profileCountBefore);
        assertThat(projectionVersionCountAfterFirstPublish).isBetween(projectionVersionCountBefore, projectionVersionCountBefore + 1);
        assertThat(versions.findByProviderIdOrderByVersionNumberDesc(profile.getProviderId()))
                .hasSize((int) projectionVersionCountAfterFirstPublish);
    }

    private PublicProfileDraftWorkspaceRecord verifiedDraft() {
        return new PublicProfileDraftWorkspaceRecord(
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                "draft-1",
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.CLINIC,
                UUID.fromString("8e5a6d56-08f8-47f1-99f4-f79b22aaef48"),
                "VERIFIED",
                "ENABLED",
                "UNPUBLISHED",
                "READY_FOR_REVIEW",
                "READY",
                100,
                20,
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
                                                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "13:00"),
                                                Map.of("dayOfWeek", "MONDAY", "startTime", "16:00", "endTime", "20:00"),
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
                        20
                ),
                List.of(),
                Map.<String, PublicProfileDraftFieldSourceRecord>of()
        );
    }
}
