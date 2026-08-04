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
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.ProviderPublicProfileDraftService;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionUpdateRequest;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftMediaUploadRecord;
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

    private ProviderPublicProfileDraftService service;
    private ProviderOwnershipService ownershipService;
    private ProviderPublicProfileService publicProfileService;
    private DiscoverPublicProfileSubmissionRepository submissionRepository;
    private ObjectStorageService storageService;

    @BeforeEach
    void setUp() {
        ownershipService = mock(ProviderOwnershipService.class);
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
        assertThat(workspace.establishedYear()).isEqualTo(2022);
        assertThat(workspace.readiness().ready()).isTrue();
        assertThat(workspace.readiness().completenessPercentage()).isEqualTo(100);
        assertThat(workspace.readiness().missingMandatoryFields()).isEmpty();
        assertThat(workspace.readiness().invalidFields()).isEmpty();
        assertThat(workspace.readiness().blockingReasons()).contains("TENANT_CONSENT_DISABLED");
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
