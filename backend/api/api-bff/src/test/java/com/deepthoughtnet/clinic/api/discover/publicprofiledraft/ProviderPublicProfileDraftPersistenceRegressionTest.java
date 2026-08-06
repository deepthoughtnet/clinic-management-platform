package com.deepthoughtnet.clinic.api.discover.publicprofiledraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.config.PersistenceScanConfig;
import com.deepthoughtnet.clinic.api.support.PostgresTestContainerSupport;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderDocumentType;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.clinic.service.model.ClinicProfileRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ClinicProfileConsentLookup;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionUpdateRequest;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftMediaUploadRecord;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.PublicProfileModerationModels.PublicProfileSubmissionEligibilityRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftEntity;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftRepository;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftVersionEntity;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftVersionRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PersistenceScanConfig.class)
class ProviderPublicProfileDraftPersistenceRegressionTest extends PostgresTestContainerSupport {
    private static final UUID PROVIDER_ACCOUNT_ID = UUID.fromString("22012201-2201-2201-2201-220122012201");
    private static final UUID PUBLIC_PROFILE_REFERENCE = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
    private static final String PUBLIC_PROFILE_REFERENCE_VALUE = PUBLIC_PROFILE_REFERENCE.toString();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private DiscoverPublicProfileDraftRepository draftRepository;

    @Autowired
    private DiscoverPublicProfileDraftVersionRepository versionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProviderPublicProfileDraftService service;
    private ProviderOwnershipService ownershipService;
    private ClinicProfileConsentLookup clinicProfileService;
    private ProviderPublicProfileService publicProfileService;
    private DiscoverPublicProfileSubmissionRepository submissionRepository;
    private ObjectStorageService storageService;

    @BeforeEach
    void setUp() {
        ownershipService = mock(ProviderOwnershipService.class);
        clinicProfileService = mock(ClinicProfileConsentLookup.class);
        publicProfileService = mock(ProviderPublicProfileService.class);
        submissionRepository = mock(DiscoverPublicProfileSubmissionRepository.class);
        storageService = mock(ObjectStorageService.class);

        OwnershipRecord ownershipRecord = new OwnershipRecord(
                UUID.randomUUID(),
                PUBLIC_PROFILE_REFERENCE_VALUE,
                PublicProfileType.CLINIC,
                PROVIDER_ACCOUNT_ID,
                PublicProfileOwnershipStatus.VERIFIED,
                "HEALTHCARE_INITIATED_CONNECTION",
                PUBLIC_PROFILE_REFERENCE_VALUE,
                1L,
                OffsetDateTime.now().minusHours(2),
                null,
                null,
                null,
                true,
                "Verified ownership",
                "{}",
                OffsetDateTime.now().minusHours(2),
                OffsetDateTime.now().minusHours(1)
        );
        MembershipRecord membershipRecord = new MembershipRecord(
                UUID.randomUUID(),
                PUBLIC_PROFILE_REFERENCE_VALUE,
                PROVIDER_ACCOUNT_ID,
                PublicProfileMembershipRole.OWNER,
                "ACTIVE",
                1L,
                "Owner membership",
                OffsetDateTime.now().minusHours(2),
                OffsetDateTime.now().minusHours(1)
        );

        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE))
                .thenReturn(Optional.of(ownershipRecord));
        when(ownershipService.listMemberships(PUBLIC_PROFILE_REFERENCE_VALUE))
                .thenReturn(List.of(membershipRecord));
        when(ownershipService.findLatestVerifiedOwnership(PROVIDER_ACCOUNT_ID))
                .thenReturn(Optional.of(ownershipRecord));
        when(clinicProfileService.findDiscoverPublicListingEnabled(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.of(true));
        when(publicProfileService.findLifecycleByProviderId(PUBLIC_PROFILE_REFERENCE))
                .thenReturn(Optional.of(new PublicProfileLifecycleRecord(
                        PUBLIC_PROFILE_REFERENCE,
                        com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType.CLINIC,
                        "HEALTHCARE_CLINIC_PROFILE",
                        PUBLIC_PROFILE_REFERENCE_VALUE,
                        1L,
                        OffsetDateTime.now().minusHours(2),
                        "green-valley-family-clinic",
                        "Green Valley Family Clinic",
                        "Pune",
                        "Wakad",
                        "NOT_AVAILABLE",
                        "UNPUBLISHED",
                        OffsetDateTime.now().minusHours(2),
                        null,
                        0L,
                        "/discover/clinics/green-valley-family-clinic"
                )));
        when(publicProfileService.isSlugReserved("green-valley-family-clinic", null)).thenReturn(false);
        when(publicProfileService.isSlugReserved(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull())).thenReturn(false);
        when(submissionRepository.findFirstByPublicProfileReferenceAndCurrentTrueOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE_VALUE))
                .thenReturn(Optional.empty());

        service = new ProviderPublicProfileDraftService(
                draftRepository,
                versionRepository,
                ownershipService,
                clinicProfileService,
                publicProfileService,
                submissionRepository,
                storageService,
                new ObjectMapper()
        );
    }

    @Test
    void draftVersionEntityMatchesMigrationSchema() {
        assertThat(DiscoverPublicProfileDraftVersionEntity.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertThat(DiscoverPublicProfileDraftVersionEntity.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("rowVersion");
        assertThat(jdbcTemplate.queryForList("""
                        select column_name
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'discover_public_profile_draft_versions'
                          and column_name = 'row_version'
                        """))
                .isEmpty();
    }

    @Test
    void createDraftPersistsCurrentDraftAndVersionOne() {
        PublicProfileDraftWorkspaceRecord workspace = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(workspace.currentVersion()).isEqualTo(1);
        assertThat(workspace.draftReference()).isNotBlank();
        assertThat(workspace.tenantConsentStatus()).isEqualTo("ENABLED");
        assertThat(workspace.readiness().ready()).isFalse();
        assertThat(workspace.contentStatus()).isEqualTo("DRAFT_INCOMPLETE");
        assertThat(workspace.readiness().readinessStatus()).isEqualTo("INCOMPLETE");
        assertThat(workspace.readiness().completenessPercentage()).isLessThan(100);
        assertThat(workspace.readiness().blockingReasons()).isEmpty();
        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(workspace.draftReference()))
                .hasSize(1)
                .first()
                .satisfies(version -> assertThat(version.getVersionNumber()).isEqualTo(1));
    }

    @Test
    void loadDraftReadsVersionWithoutMissingColumn() {
        PublicProfileDraftWorkspaceRecord workspace = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        entityManager.clear();

        DiscoverPublicProfileDraftEntity loaded = draftRepository.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE_VALUE).orElseThrow();
        assertThat(loaded.getDraftReference()).isEqualTo(workspace.draftReference());
        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(loaded.getDraftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactly(1);
    }

    @Test
    void previewLoadsPersistedDraft() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        entityManager.clear();

        PublicProfileDraftWorkspaceRecord preview = service.preview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(preview.draftReference()).isEqualTo(created.draftReference());
        assertThat(preview.currentVersion()).isEqualTo(1);
        assertThat(preview.sections()).isNotEmpty();
    }

    @Test
    void versionHistoryLoadsAllImmutableVersions() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                Map.of(
                        "displayName", "Green Valley Family Clinic",
                        "shortTagline", "Family care in Wakad",
                        "description", "Green Valley Family Clinic provides clear, practical, and continuity-focused outpatient care for families across Wakad and nearby Pune neighborhoods. The practice emphasizes accessible communication, careful follow-up, and trustworthy day-to-day primary care.",
                        "philosophy", "Patient-first family care",
                        "establishedYear", "2018",
                        "registrationNumber", "MH-REG-12345",
                        "emergencyAvailability", "24x7"
                ),
                Long.valueOf(1L),
                "Expanded about section"
        ));
        entityManager.clear();

        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactly(2, 1);
    }

    @Test
    void noOpSaveDoesNotCreateVersion() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        Map<String, Object> currentAbout = created.sections().stream()
                .filter(section -> "about".equals(section.key()))
                .findFirst()
                .orElseThrow()
                .content();

        service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                currentAbout,
                created.currentVersion() == 1 ? 1L : (long) created.currentVersion(),
                "No-op save"
        ));

        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactly(1);
    }

    private void markDraftReadinessStale(String publicProfileReference) {
        jdbcTemplate.update("""
                update discover_public_profile_drafts
                set content_status = 'DRAFT_INCOMPLETE',
                    readiness_status = 'INCOMPLETE',
                    completeness_percentage = 100,
                    readiness_json = ?,
                    updated_at = now(),
                    last_saved_at = now()
                where public_profile_reference = ?
                """,
                """
                {"readinessStatus":"INCOMPLETE","ready":false,"completenessPercentage":100,"missingMandatoryFields":[],"recommendedFields":["gallery","establishedYear","facilities","languages","fees","website","whatsappNumber","metaTitle","metaDescription"],"invalidFields":["invalid_established_year"],"warnings":[],"blockingReasons":[],"lastEvaluatedAt":"2026-08-04T02:07:33.357711686Z","evaluatedDraftVersion":15}
                """,
                publicProfileReference
        );
        entityManager.clear();
    }

    private void markDraftMissingMandatoryField(String publicProfileReference, String fieldPath) throws Exception {
        String contentJson = jdbcTemplate.queryForObject("""
                select content_json from discover_public_profile_drafts where public_profile_reference = ?
                """, String.class, publicProfileReference);
        Map<String, Object> content = objectMapper.readValue(contentJson, LinkedHashMap.class);
        if ("addressLine1".equals(fieldPath)) {
            ((Map<String, Object>) content.get("contact")).remove("addressLine1");
        }
        jdbcTemplate.update("""
                update discover_public_profile_drafts
                set content_json = ?,
                    content_status = 'DRAFT_INCOMPLETE',
                    readiness_status = 'INCOMPLETE',
                    completeness_percentage = 90,
                    readiness_json = ?,
                    updated_at = now(),
                    last_saved_at = now()
                where public_profile_reference = ?
                """,
                objectMapper.writeValueAsString(content),
                """
                {"readinessStatus":"INCOMPLETE","ready":false,"completenessPercentage":90,"missingMandatoryFields":["addressLine1"],"recommendedFields":["gallery","establishedYear","facilities","languages","fees","website","whatsappNumber","metaTitle","metaDescription"],"invalidFields":[],"warnings":[],"blockingReasons":[],"lastEvaluatedAt":"2026-08-04T02:07:33.357711686Z","evaluatedDraftVersion":15}
                """,
                publicProfileReference
        );
        entityManager.clear();
    }

    @Test
    void completeDraftPersistsReadyStatus() {
        PublicProfileDraftWorkspaceRecord created = buildReadyDraft();
        markDraftReadinessStale(created.publicProfileReference());

        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        Map<String, Object> overview = repaired.sections().stream()
                .filter(section -> "overview".equals(section.key()))
                .findFirst()
                .orElseThrow()
                .content();

        assertThat(repaired.currentVersion()).isEqualTo(created.currentVersion());
        assertThat(repaired.contentStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(repaired.readinessStatus()).isEqualTo("READY");
        assertThat(repaired.readiness().ready()).isTrue();
        assertThat(repaired.readiness().completenessPercentage()).isEqualTo(100);
        assertThat(repaired.readiness().invalidFields()).isEmpty();
        assertThat(repaired.readiness().missingMandatoryFields()).isEmpty();
        assertThat(repaired.readiness().evaluatedDraftVersion()).isEqualTo(created.currentVersion());
        assertThat(overview.get("contentStatus")).isEqualTo("READY_FOR_REVIEW");
        assertThat(overview.get("completenessPercentage")).isEqualTo(100);
        assertThat(overview.get("summaryStatus")).isEqualTo("READY");
    }

    @Test
    void incompleteDraftPersistsIncompleteStatus() throws Exception {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        markDraftMissingMandatoryField(created.publicProfileReference(), "addressLine1");

        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(repaired.contentStatus()).isEqualTo("DRAFT_INCOMPLETE");
        assertThat(repaired.readinessStatus()).isEqualTo("INCOMPLETE");
        assertThat(repaired.readiness().ready()).isFalse();
        assertThat(repaired.readiness().missingMandatoryFields()).contains("addressLine1");
    }

    @Test
    void completenessAndReadinessStatusCannotContradict() {
        PublicProfileDraftWorkspaceRecord created = buildReadyDraft();
        markDraftReadinessStale(created.publicProfileReference());

        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(repaired.readiness().ready()).isTrue();
        assertThat(repaired.contentStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(repaired.readinessStatus()).isEqualTo("READY");
        assertThat(repaired.readiness().completenessPercentage()).isEqualTo(repaired.completenessPercentage());
    }

    @Test
    void tenantConsentDoesNotChangeContentReadiness() {
        PublicProfileDraftWorkspaceRecord created = buildReadyDraft();
        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        ProviderPublicProfileModerationService moderationService = new ProviderPublicProfileModerationService(
                service,
                ownershipService,
                publicProfileService,
                submissionRepository,
                mock(com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingRepository.class),
                mock(com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository.class),
                storageService,
                new ObjectMapper()
        );
        PublicProfileSubmissionEligibilityRecord eligibility = moderationService.submissionEligibility(PROVIDER_ACCOUNT_ID, repaired.publicProfileReference(), false);

        assertThat(repaired.readiness().ready()).isTrue();
        assertThat(eligibility.submissionEligible()).isFalse();
        assertThat(eligibility.submissionBlockers()).contains("TENANT_CONSENT_REQUIRED");
        assertThat(eligibility.submissionBlockers()).doesNotContain("PROFILE_INCOMPLETE");
    }

    @Test
    void readinessRecalculationUsesCurrentDraftVersion() {
        PublicProfileDraftWorkspaceRecord created = buildReadyDraft();
        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(repaired.readiness().evaluatedDraftVersion()).isEqualTo(created.currentVersion());
    }

    @Test
    void mediaCompletionTransitionsDraftToReady() throws Exception {
        PublicProfileDraftWorkspaceRecord created = buildDraftWithoutMedia();
        service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.LOGO,
                "logo.png",
                "image/png",
                12,
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4},
                null
        );
        service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.COVER_IMAGE,
                "cover.png",
                "image/png",
                12,
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4},
                null
        );
        entityManager.clear();

        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(repaired.contentStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(repaired.readinessStatus()).isEqualTo("READY");
        assertThat(repaired.readiness().missingMandatoryFields()).isEmpty();
        assertThat(repaired.readiness().ready()).isTrue();
    }

    @Test
    void timingCompletionTransitionsDraftToReady() throws Exception {
        PublicProfileDraftWorkspaceRecord created = buildDraftWithoutTimings();
        service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "timings",
                Map.of(
                        "timezone", "Asia/Kolkata",
                        "weekly", List.of(
                                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "13:00"),
                                Map.of("dayOfWeek", "MONDAY", "startTime", "16:00", "endTime", "20:00")
                        )
                ),
                Long.valueOf(created.currentVersion()),
                "Complete timings"
        ));
        entityManager.clear();

        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(repaired.contentStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(repaired.readinessStatus()).isEqualTo("READY");
    }

    @Test
    void noOpReadinessRecalculationDoesNotCreateVersion() {
        PublicProfileDraftWorkspaceRecord created = buildReadyDraft();
        List<Integer> versionsBefore = versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference())
                .stream()
                .map(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .toList();

        service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactlyElementsOf(versionsBefore);
    }

    @Test
    void existingCompleteDraftCanBeReconciledIdempotently() {
        PublicProfileDraftWorkspaceRecord created = buildReadyDraft();
        List<Integer> versionsBefore = versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference())
                .stream()
                .map(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .toList();
        markDraftReadinessStale(created.publicProfileReference());

        PublicProfileDraftWorkspaceRecord repairedOnce = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        PublicProfileDraftWorkspaceRecord repairedTwice = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(repairedOnce.contentStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(repairedTwice.contentStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactlyElementsOf(versionsBefore);
    }

    @Test
    void submitEligibilityIsBlockedOnlyByConsentAfterReadinessRepair() {
        PublicProfileDraftWorkspaceRecord created = buildReadyDraft();
        PublicProfileDraftWorkspaceRecord repaired = service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        ProviderPublicProfileModerationService moderationService = new ProviderPublicProfileModerationService(
                service,
                ownershipService,
                publicProfileService,
                submissionRepository,
                mock(com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingRepository.class),
                mock(com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository.class),
                storageService,
                new ObjectMapper()
        );
        PublicProfileSubmissionEligibilityRecord eligibility = moderationService.submissionEligibility(PROVIDER_ACCOUNT_ID, repaired.publicProfileReference(), false);

        assertThat(eligibility.submissionEligible()).isFalse();
        assertThat(eligibility.submissionBlockers()).containsExactly("TENANT_CONSENT_REQUIRED");
        assertThat(eligibility.moderationStatus()).isEqualTo("NOT_SUBMITTED");
        assertThat(eligibility.publicationStatus()).isEqualTo("UNPUBLISHED");
    }

    @Test
    void readyDraftWithEnabledConsentCanSubmitForReview() {
        PublicProfileDraftWorkspaceRecord repaired = buildReadyDraft();

        ProviderPublicProfileModerationService moderationService = new ProviderPublicProfileModerationService(
                service,
                ownershipService,
                publicProfileService,
                submissionRepository,
                mock(com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileReviewFindingRepository.class),
                mock(com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfilePublicationRepository.class),
                storageService,
                new ObjectMapper()
        );
        PublicProfileSubmissionEligibilityRecord eligibility = moderationService.submissionEligibility(PROVIDER_ACCOUNT_ID, repaired.publicProfileReference(), true);

        assertThat(repaired.tenantConsentStatus()).isEqualTo("ENABLED");
        assertThat(repaired.contentStatus()).isEqualTo("READY_FOR_REVIEW");
        assertThat(repaired.readinessStatus()).isEqualTo("READY");
        assertThat(repaired.allowedActions()).contains("SUBMIT_FOR_REVIEW");
        assertThat(eligibility.submissionEligible()).isTrue();
        assertThat(eligibility.submissionBlockers()).isEmpty();
        assertThat(draftRepository.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE_VALUE).orElseThrow().getTenantConsentStatus())
                .isEqualTo("ENABLED");
        assertThat(draftRepository.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE_VALUE).orElseThrow().getContentStatus())
                .isEqualTo("READY_FOR_REVIEW");
        assertThat(draftRepository.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE_VALUE).orElseThrow().getReadinessStatus())
                .isEqualTo("READY");
    }

    private Map<String, Object> readyAboutSection() {
        Map<String, Object> about = new LinkedHashMap<>();
        about.put("displayName", "Green Valley Family Clinic");
        about.put("shortTagline", "Comprehensive family healthcare with compassionate doctors and modern facilities.");
        about.put("description", "Green Valley Family Clinic provides clear, practical, continuity-focused outpatient care for families across Wakad and nearby Pune neighborhoods. The practice emphasizes accessible communication, careful follow-up, and trustworthy day-to-day primary care with modern, patient-centered services.");
        about.put("philosophy", "Patient-first family care");
        about.put("establishedYear", "2022");
        about.put("registrationNumber", "PMC/CLINIC/2022/10458");
        about.put("emergencyAvailability", "Available during clinic hours");
        return about;
    }

    private Map<String, Object> readyContactSection() {
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("publicPhone", "+91 98765 02201");
        contact.put("publicEmail", "contact@greenvalleyclinic.in");
        contact.put("website", "https://www.greenvalleyclinic.in");
        contact.put("whatsappNumber", "+91 98765 02201");
        contact.put("addressLine1", "Survey No. 58, Green Valley Medical Centre");
        contact.put("addressLine2", "Near Bhumkar Chowk");
        contact.put("area", "Wakad");
        contact.put("city", "Pune");
        contact.put("state", "Maharashtra");
        contact.put("country", "India");
        contact.put("postalCode", "411057");
        contact.put("phoneVisible", true);
        contact.put("emailVisible", true);
        contact.put("whatsappVisible", false);
        return contact;
    }

    private Map<String, Object> readyServicesSection() {
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("items", List.of("General Physician Consultation", "Family Medicine"));
        return services;
    }

    private Map<String, Object> readySpecialitiesSection() {
        Map<String, Object> specialities = new LinkedHashMap<>();
        specialities.put("items", List.of("Family Medicine", "General Medicine"));
        specialities.put("primary", "Family Medicine");
        return specialities;
    }

    private Map<String, Object> readyTimingsSection() {
        Map<String, Object> timings = new LinkedHashMap<>();
        timings.put("timezone", "Asia/Kolkata");
        timings.put("weekly", List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00", "endTime", "13:00"),
                Map.of("dayOfWeek", "MONDAY", "startTime", "16:00", "endTime", "20:00"),
                Map.of("dayOfWeek", "TUESDAY", "startTime", "09:00", "endTime", "13:00"),
                Map.of("dayOfWeek", "TUESDAY", "startTime", "16:00", "endTime", "20:00")
        ));
        return timings;
    }

    private PublicProfileDraftWorkspaceRecord buildReadyDraft() {
        PublicProfileDraftWorkspaceRecord workspace = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                readyAboutSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete about section"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "contact",
                readyContactSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete contact section"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "services",
                readyServicesSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete services"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "specialities",
                readySpecialitiesSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete specialities"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "timings",
                readyTimingsSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete timings"
        ));
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        workspace = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.LOGO,
                "logo.png",
                "image/png",
                png.length,
                png,
                null
        ).draft();
        workspace = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.COVER_IMAGE,
                "cover.png",
                "image/png",
                png.length,
                png,
                null
        ).draft();
        return service.recalculateReadiness(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
    }

    private PublicProfileDraftWorkspaceRecord buildDraftWithoutMedia() {
        PublicProfileDraftWorkspaceRecord workspace = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                readyAboutSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete about section"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "contact",
                readyContactSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete contact section"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "services",
                readyServicesSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete services"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "specialities",
                readySpecialitiesSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete specialities"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "timings",
                readyTimingsSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete timings"
        ));
        return workspace;
    }

    private PublicProfileDraftWorkspaceRecord workspaceWithMediaForCurrentDraft() {
        PublicProfileDraftWorkspaceRecord workspace = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.LOGO,
                "logo.png",
                "image/png",
                12,
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4},
                null
        ).draft();
        workspace = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.COVER_IMAGE,
                "cover.png",
                "image/png",
                12,
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4},
                null
        ).draft();
        return workspace;
    }

    private PublicProfileDraftWorkspaceRecord buildDraftWithoutTimings() {
        PublicProfileDraftWorkspaceRecord workspace = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                readyAboutSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete about section"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "contact",
                readyContactSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete contact section"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "services",
                readyServicesSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete services"
        ));
        workspace = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "specialities",
                readySpecialitiesSection(),
                Long.valueOf(workspace.currentVersion()),
                "Complete specialities"
        ));
        workspace = workspaceWithMediaForCurrentDraft();
        return workspace;
    }

    @Test
    void providerCanUploadLogoAndPreviewMedia() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        PublicProfileDraftMediaUploadRecord uploaded = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.LOGO,
                "logo.png",
                "image/png",
                png.length,
                png,
                null
        );

        assertThat(uploaded.mediaReference()).isNotBlank();
        assertThat(uploaded.draft().currentVersion()).isEqualTo(created.currentVersion() + 1);
        assertThat(uploaded.draft().sections()).anySatisfy(section -> {
            if ("media".equals(section.key())) {
                assertThat(section.content()).containsEntry("logoDocumentId", uploaded.mediaReference());
            }
        });
        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactly(2, 1);
        verify(storageService).putObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("image/png"),
                org.mockito.ArgumentMatchers.argThat(bytes -> Arrays.equals(bytes, png))
        );
    }

    @Test
    void retryDoesNotCreateDuplicateVersion() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        PublicProfileDraftMediaUploadRecord first = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.LOGO,
                "logo.png",
                "image/png",
                png.length,
                png,
                null
        );
        PublicProfileDraftMediaUploadRecord second = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.LOGO,
                "logo.png",
                "image/png",
                png.length,
                png,
                null
        );

        assertThat(second.mediaReference()).isEqualTo(first.mediaReference());
        assertThat(second.draft().currentVersion()).isEqualTo(first.draft().currentVersion());
        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactly(2, 1);
    }

    @Test
    void invalidFileTypeIsRejected() {
        service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThatThrownBy(() -> service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.LOGO,
                "logo.gif",
                "image/gif",
                8,
                new byte[] {1, 2, 3, 4, 5, 6, 7, 8},
                null
        )).isInstanceOf(com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException.class)
                .hasMessageContaining("Supported formats");
    }

    @Test
    void changedSaveCreatesOneNewVersion() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        PublicProfileDraftWorkspaceRecord updated = service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                Map.of(
                        "displayName", "Green Valley Family Clinic",
                        "shortTagline", "Family care in Wakad",
                        "description", "Green Valley Family Clinic provides clear, practical, and continuity-focused outpatient care for families across Wakad and nearby Pune neighborhoods. The practice emphasizes accessible communication, careful follow-up, and trustworthy day-to-day primary care.",
                        "philosophy", "Patient-first family care",
                        "establishedYear", "2018",
                        "registrationNumber", "MH-REG-12345",
                        "emergencyAvailability", "24x7"
                ),
                Long.valueOf(created.currentVersion()),
                "Changed about section"
        ));

        assertThat(updated.currentVersion()).isEqualTo(2);
        assertThat(versionRepository.findByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .containsExactly(2, 1);
    }

    @Test
    void staleCurrentDraftVersionIsRejected() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                Map.of(
                        "displayName", "Green Valley Family Clinic",
                        "shortTagline", "Family care in Wakad",
                        "description", "Green Valley Family Clinic provides clear, practical, and continuity-focused outpatient care for families across Wakad and nearby Pune neighborhoods. The practice emphasizes accessible communication, careful follow-up, and trustworthy day-to-day primary care.",
                        "philosophy", "Patient-first family care",
                        "establishedYear", "2018",
                        "registrationNumber", "MH-REG-12345",
                        "emergencyAvailability", "24x7"
                ),
                Long.valueOf(created.currentVersion()),
                "Changed about section"
        ));

        assertThatThrownBy(() -> service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                Map.of("displayName", "Green Valley Family Clinic"),
                1L,
                "Stale write"
        ))).isInstanceOf(com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException.class)
                .hasMessageContaining("updated elsewhere");
    }

    @Test
    void historicalVersionCannotBeUpdated() {
        service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(DiscoverPublicProfileDraftVersionEntity.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertThat(DiscoverPublicProfileDraftVersionEntity.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("setRowVersion");
    }

    @Test
    void missingDraftReturnsPublicProfileDraftNotFound() {
        assertThatThrownBy(() -> service.preview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE))
                .isInstanceOf(com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException.class)
                .hasMessageContaining("Public profile draft not found");
    }

    @Test
    void previewDisplaysPersistedMedia() {
        service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        PublicProfileDraftMediaUploadRecord uploaded = service.uploadMedia(
                PROVIDER_ACCOUNT_ID,
                PUBLIC_PROFILE_REFERENCE_VALUE,
                ProviderDocumentType.COVER_IMAGE,
                "cover.png",
                "image/png",
                png.length,
                png,
                null
        );
        when(storageService.getObjectBytes(org.mockito.ArgumentMatchers.anyString())).thenReturn(png);

        var preview = service.preview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);

        assertThat(preview.sections()).anySatisfy(section -> {
            if ("media".equals(section.key())) {
                assertThat(section.content()).containsEntry("coverDocumentId", uploaded.mediaReference());
            }
        });
        assertThat(service.downloadMedia(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, uploaded.mediaReference()).bytes())
                .isEqualTo(png);
    }

    @Test
    void sqlFailureIsNotConvertedToDraftNotFound() {
        DiscoverPublicProfileDraftRepository failingDraftRepository = mock(DiscoverPublicProfileDraftRepository.class);
        when(failingDraftRepository.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE_VALUE))
                .thenThrow(new InvalidDataAccessResourceUsageException("column row_version does not exist"));

        ProviderPublicProfileDraftService failingService = new ProviderPublicProfileDraftService(
                failingDraftRepository,
                versionRepository,
                ownershipService,
                clinicProfileService,
                publicProfileService,
                submissionRepository,
                storageService,
                new ObjectMapper()
        );

        assertThatThrownBy(() -> failingService.preview(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE))
                .isInstanceOf(InvalidDataAccessResourceUsageException.class)
                .hasMessageContaining("row_version");
    }

    @Test
    void submitModerationCapturesExistingDraftVersion() {
        PublicProfileDraftWorkspaceRecord created = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE);
        service.saveSection(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE_VALUE, new PublicProfileDraftSectionUpdateRequest(
                "about",
                Map.of(
                        "displayName", "Green Valley Family Clinic",
                        "shortTagline", "Family care in Wakad",
                        "description", "Green Valley Family Clinic provides clear, practical, and continuity-focused outpatient care for families across Wakad and nearby Pune neighborhoods. The practice emphasizes accessible communication, careful follow-up, and trustworthy day-to-day primary care.",
                        "philosophy", "Patient-first family care",
                        "establishedYear", "2018",
                        "registrationNumber", "MH-REG-12345",
                        "emergencyAvailability", "24x7"
                ),
                Long.valueOf(created.currentVersion()),
                "Changed about section"
        ));

        assertThat(versionRepository.findFirstByDraftReferenceOrderByVersionNumberDesc(created.draftReference()))
                .isPresent()
                .get()
                .extracting(DiscoverPublicProfileDraftVersionEntity::getVersionNumber)
                .isEqualTo(2);
    }

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @Import(PersistenceScanConfig.class)
    static class TestApplication {
    }
}
