package com.deepthoughtnet.clinic.discover.publicprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderDocumentRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderLocationRepository;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderServiceRepository;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipConflictException;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileSlugRepository;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileSlugEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.db.DiscoverPublicProviderProfileVersionRepository;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProviderPublicProfileServiceTest {
    private static final UUID LIFECYCLE_PROVIDER_ID = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
    private static final UUID LEGACY_PROVIDER_ID = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");
    private static final UUID VERSION_ID = UUID.fromString("07aefe9f-8ab8-4c67-9436-b5bc725fb1a5");
    private static final OffsetDateTime PUBLISHED_AT = OffsetDateTime.parse("2026-08-06T04:22:14.4331Z");

    @Mock
    private DiscoverPublicProviderProfileRepository profiles;
    @Mock
    private DiscoverPublicProviderProfileVersionRepository versions;
    @Mock
    private DiscoverPublicProviderProfileSlugRepository slugs;
    @Mock
    private ProviderLocationRepository locations;
    @Mock
    private ProviderServiceRepository services;
    @Mock
    private ProviderDocumentRepository documents;
    @Mock
    private ObjectStorageService storageService;

    private ProviderPublicProfileService service;

    @BeforeEach
    void setUp() {
        service = new ProviderPublicProfileService(
                profiles,
                versions,
                slugs,
                locations,
                services,
                documents,
                storageService,
                new ObjectMapper()
        );
    }

    @Test
    void upsertLifecycleProfileReusesExistingCanonicalSlugOwnerForSameClinic() {
        PublicProviderProfileModels.PublicProviderProfileSnapshot snapshot = lifecycleSnapshot();
        DiscoverPublicProviderProfileEntity legacyProfile = legacyProfile();
        DiscoverPublicProviderProfileVersionEntity legacyVersion = DiscoverPublicProviderProfileVersionEntity.create(
                LEGACY_PROVIDER_ID,
                17,
                1,
                null,
                "PUBLISHED",
                "HEALTHCARE_CLINIC",
                "startup.reconcile",
                "legacy-hash",
                "{}",
                "green-valley-family-clinic",
                PUBLISHED_AT
        );

        when(profiles.findByProviderId(LIFECYCLE_PROVIDER_ID)).thenReturn(Optional.empty());
        when(profiles.findByCanonicalSlug("green-valley-family-clinic")).thenReturn(Optional.of(legacyProfile));
        when(profiles.findByProviderId(LEGACY_PROVIDER_ID)).thenReturn(Optional.of(legacyProfile));
        when(versions.findByProviderIdAndVersionNumber(LEGACY_PROVIDER_ID, 20)).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PublicProviderProfileModels.PublicProviderPublicationRecord publication = service.upsertLifecycleProfile(
                snapshot,
                20,
                "APPROVED",
                "PUBLISHED",
                "Publish approved version",
                PUBLISHED_AT,
                "PUBLISHED",
                "PROVIDER_PUBLIC_PROFILE_DRAFT",
                LIFECYCLE_PROVIDER_ID.toString(),
                20L,
                PUBLISHED_AT,
                0L
        );

        assertThat(publication.providerId()).isEqualTo(LEGACY_PROVIDER_ID);
        assertThat(publication.canonicalSlug()).isEqualTo("green-valley-family-clinic");
        assertThat(publication.publicPath()).isEqualTo("/discover/clinics/green-valley-family-clinic");

        ArgumentCaptor<DiscoverPublicProviderProfileVersionEntity> versionCaptor = ArgumentCaptor.forClass(DiscoverPublicProviderProfileVersionEntity.class);
        verify(versions).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getProviderId()).isEqualTo(LEGACY_PROVIDER_ID);
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(20);
        verify(profiles).findByProviderId(LIFECYCLE_PROVIDER_ID);
        verify(profiles).findByCanonicalSlug("green-valley-family-clinic");
        verify(profiles, times(2)).findByProviderId(LEGACY_PROVIDER_ID);
        verify(slugs).findFirstBySlug("green-valley-family-clinic");
    }

    @Test
    void upsertLifecycleProfileAppendsProjectionHistoryWhenApprovedVersionNumberIsOccupied() {
        PublicProviderProfileModels.PublicProviderProfileSnapshot snapshot = lifecycleSnapshot();
        DiscoverPublicProviderProfileEntity legacyProfile = legacyProfile();
        DiscoverPublicProviderProfileVersionEntity occupiedVersion = DiscoverPublicProviderProfileVersionEntity.create(
                LEGACY_PROVIDER_ID,
                20,
                7,
                "APPROVED",
                "PUBLISHED",
                "HEALTHCARE_CLINIC",
                "Historical projection",
                "historical-content-hash",
                "{\"displayName\":\"Historical clinic content\"}",
                "green-valley-family-clinic",
                PUBLISHED_AT.minusDays(1)
        );
        DiscoverPublicProviderProfileSlugEntity existingAlias = DiscoverPublicProviderProfileSlugEntity.create(
                LEGACY_PROVIDER_ID,
                occupiedVersion.getId(),
                "green-valley-family-clinic",
                20,
                true,
                PUBLISHED_AT.minusDays(1)
        );

        when(profiles.findByProviderId(LIFECYCLE_PROVIDER_ID)).thenReturn(Optional.empty());
        when(profiles.findByCanonicalSlug("green-valley-family-clinic")).thenReturn(Optional.of(legacyProfile));
        when(profiles.findByProviderId(LEGACY_PROVIDER_ID)).thenReturn(Optional.of(legacyProfile));
        when(versions.findByProviderIdAndVersionNumber(LEGACY_PROVIDER_ID, 20)).thenReturn(Optional.of(occupiedVersion));
        when(versions.findFirstByProviderIdAndSourceSubmissionVersionNumberOrderByVersionNumberDesc(
                LEGACY_PROVIDER_ID, 20)).thenReturn(Optional.empty());
        when(versions.findFirstByProviderIdOrderByVersionNumberDesc(LEGACY_PROVIDER_ID)).thenReturn(Optional.of(occupiedVersion));
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(slugs.findFirstBySlug("green-valley-family-clinic")).thenReturn(Optional.of(existingAlias));
        when(slugs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PublicProviderProfileModels.PublicProviderPublicationRecord publication = service.upsertLifecycleProfile(
                snapshot,
                20,
                "APPROVED",
                "PUBLISHED",
                "Publish approved version",
                PUBLISHED_AT,
                "PUBLISHED",
                "PROVIDER_PUBLIC_PROFILE_DRAFT",
                LIFECYCLE_PROVIDER_ID.toString(),
                20L,
                PUBLISHED_AT,
                0L
        );

        ArgumentCaptor<DiscoverPublicProviderProfileVersionEntity> versionCaptor = ArgumentCaptor.forClass(DiscoverPublicProviderProfileVersionEntity.class);
        verify(versions).save(versionCaptor.capture());
        DiscoverPublicProviderProfileVersionEntity approvedProjection = versionCaptor.getValue();
        assertThat(publication.publishedVersionNumber()).isEqualTo(21);
        assertThat(approvedProjection.getVersionNumber()).isEqualTo(21);
        assertThat(approvedProjection.getSourceSubmissionVersionNumber()).isEqualTo(20);
        assertThat(approvedProjection.getSnapshotHash()).isNotEqualTo(occupiedVersion.getSnapshotHash());
        assertThat(occupiedVersion.getSnapshotHash()).isEqualTo("historical-content-hash");
        assertThat(legacyProfile.getLatestPublishedVersionId()).isEqualTo(approvedProjection.getId());
        assertThat(legacyProfile.getLatestPublishedVersionNumber()).isEqualTo(21);
        assertThat(legacyProfile.getPublicationStatus()).isEqualTo("PUBLISHED");
        assertThat(existingAlias.getProfileVersionId()).isEqualTo(approvedProjection.getId());
        assertThat(existingAlias.getVersionNumber()).isEqualTo(21);
        assertThat(existingAlias.isActive()).isTrue();
    }

    @Test
    void repeatedApprovedPublicationReusesProjectionWhenOnlyProjectionTimestampDiffers() throws Exception {
        PublicProviderProfileModels.PublicProviderProfileSnapshot snapshot = lifecycleSnapshot();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ObjectNode previouslyPublishedSnapshot = mapper.valueToTree(snapshot);
        previouslyPublishedSnapshot.put("publishedAt", PUBLISHED_AT.minusHours(2).toString());
        DiscoverPublicProviderProfileEntity legacyProfile = legacyProfile();
        DiscoverPublicProviderProfileVersionEntity occupiedVersion = DiscoverPublicProviderProfileVersionEntity.create(
                LEGACY_PROVIDER_ID, 20, 7, "APPROVED", "PUBLISHED", "HEALTHCARE_CLINIC",
                "Historical projection", "historical-content-hash", "{}", "green-valley-family-clinic",
                PUBLISHED_AT.minusDays(1)
        );
        DiscoverPublicProviderProfileVersionEntity approvedProjection = DiscoverPublicProviderProfileVersionEntity.create(
                LEGACY_PROVIDER_ID, 21, 20, "APPROVED", "PUBLISHED", "PROVIDER_PUBLIC_PROFILE_DRAFT",
                "Publish approved version", "approved-content-hash", mapper.writeValueAsString(previouslyPublishedSnapshot),
                "green-valley-family-clinic", PUBLISHED_AT.minusHours(2)
        );
        DiscoverPublicProviderProfileSlugEntity existingAlias = DiscoverPublicProviderProfileSlugEntity.create(
                LEGACY_PROVIDER_ID, approvedProjection.getId(), "green-valley-family-clinic", 21, true,
                PUBLISHED_AT.minusHours(2)
        );

        when(profiles.findByProviderId(LIFECYCLE_PROVIDER_ID)).thenReturn(Optional.empty());
        when(profiles.findByCanonicalSlug("green-valley-family-clinic")).thenReturn(Optional.of(legacyProfile));
        when(profiles.findByProviderId(LEGACY_PROVIDER_ID)).thenReturn(Optional.of(legacyProfile));
        when(versions.findByProviderIdAndVersionNumber(LEGACY_PROVIDER_ID, 20)).thenReturn(Optional.of(occupiedVersion));
        when(versions.findFirstByProviderIdAndSourceSubmissionVersionNumberOrderByVersionNumberDesc(LEGACY_PROVIDER_ID, 20))
                .thenReturn(Optional.of(approvedProjection));
        when(slugs.findFirstBySlug("green-valley-family-clinic")).thenReturn(Optional.of(existingAlias));
        when(slugs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(profiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PublicProviderProfileModels.PublicProviderPublicationRecord publication = service.upsertLifecycleProfile(
                snapshot, 20, "APPROVED", "PUBLISHED", "Publish approved version", PUBLISHED_AT,
                "PUBLISHED", "PROVIDER_PUBLIC_PROFILE_DRAFT", LIFECYCLE_PROVIDER_ID.toString(), 20L,
                PUBLISHED_AT, 0L
        );

        assertThat(publication.publishedVersionNumber()).isEqualTo(21);
        assertThat(legacyProfile.getLatestPublishedVersionId()).isEqualTo(approvedProjection.getId());
        verify(versions, never()).save(any());
    }

    @Test
    void upsertLifecycleProfileReturnsConflictForIncompatibleSlugOwner() {
        PublicProviderProfileModels.PublicProviderProfileSnapshot snapshot = lifecycleSnapshot();
        DiscoverPublicProviderProfileEntity incompatibleProfile = DiscoverPublicProviderProfileEntity.create(
                LEGACY_PROVIDER_ID,
                ProviderType.INDIVIDUAL_DOCTOR,
                "HEALTHCARE_CLINIC",
                LEGACY_PROVIDER_ID.toString(),
                1785872344876L,
                PUBLISHED_AT,
                "green-valley-family-clinic",
                VERSION_ID,
                17,
                "Green Valley Family Clinic",
                "Green Valley Family Clinic",
                "Trusted care",
                "Family Medicine",
                "Family Medicine",
                null,
                "General Consultation",
                null,
                "Parking",
                "English",
                null,
                UUID.fromString("3d7b60eb-e869-3184-aaea-4a9719fb2cb2"),
                UUID.fromString("527da827-4f73-31db-8298-b31a2688772f"),
                null,
                "+91 98765 02201",
                "contact@greenvalleyclinic.in",
                "https://www.greenvalleyclinic.in",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "Family care",
                null,
                null,
                null,
                null,
                false,
                1,
                1,
                0,
                1,
                "CALL_TO_BOOK",
                PUBLISHED_AT
        );

        when(profiles.findByProviderId(LIFECYCLE_PROVIDER_ID)).thenReturn(Optional.empty());
        when(profiles.findByCanonicalSlug("green-valley-family-clinic")).thenReturn(Optional.of(incompatibleProfile));
        when(slugs.findFirstBySlug("green-valley-family-clinic")).thenReturn(Optional.of(
                DiscoverPublicProviderProfileSlugEntity.create(
                        LEGACY_PROVIDER_ID,
                        VERSION_ID,
                        "green-valley-family-clinic",
                        17,
                        true,
                        PUBLISHED_AT
                )
        ));
        when(versions.findByProviderIdAndVersionNumber(LIFECYCLE_PROVIDER_ID, 20)).thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.upsertLifecycleProfile(
                snapshot,
                20,
                "APPROVED",
                "PUBLISHED",
                "Publish approved version",
                PUBLISHED_AT,
                "PUBLISHED",
                "PROVIDER_PUBLIC_PROFILE_DRAFT",
                LIFECYCLE_PROVIDER_ID.toString(),
                20L,
                PUBLISHED_AT,
                0L
        ))
                .isInstanceOf(ProviderOwnershipConflictException.class)
                .hasMessage("The requested public profile URL is already used by another provider.");
    }

    @Test
    void legacyStartupProjectionCannotReplaceCurrentModeratedProjection() {
        PublicProviderProfileModels.PublicProviderProfileSnapshot legacySnapshot = lifecycleSnapshot();
        DiscoverPublicProviderProfileVersionEntity moderatedVersion = DiscoverPublicProviderProfileVersionEntity.create(
                LEGACY_PROVIDER_ID,
                20,
                20,
                "APPROVED",
                "PUBLISHED",
                "PROVIDER_PUBLIC_PROFILE_DRAFT",
                "Publish approved version",
                "approved-content-hash",
                "{}",
                "green-valley-family-clinic",
                PUBLISHED_AT
        );
        DiscoverPublicProviderProfileEntity profile = legacyProfile(
                moderatedVersion.getId(),
                20,
                "Approved Version 20 content"
        );

        when(profiles.findByProviderId(LIFECYCLE_PROVIDER_ID)).thenReturn(Optional.empty());
        when(profiles.findByCanonicalSlug("green-valley-family-clinic")).thenReturn(Optional.of(profile));
        when(profiles.findByProviderId(LEGACY_PROVIDER_ID)).thenReturn(Optional.of(profile));
        when(versions.findById(moderatedVersion.getId())).thenReturn(Optional.of(moderatedVersion));

        var result = service.upsertLifecycleProfile(
                legacySnapshot,
                1,
                null,
                "PUBLISHED",
                "startup.reconcile",
                PUBLISHED_AT.plusHours(1),
                "PUBLISHED",
                "HEALTHCARE_CLINIC",
                LEGACY_PROVIDER_ID.toString(),
                99L,
                PUBLISHED_AT.plusHours(1),
                0L
        );

        assertThat(result.publishedVersionNumber()).isEqualTo(20);
        assertThat(profile.getLatestPublishedVersionId()).isEqualTo(moderatedVersion.getId());
        assertThat(profile.getSummary()).isEqualTo("Approved Version 20 content");
        verify(versions, never()).save(any());
        verify(slugs, never()).save(any());
        verify(profiles, never()).save(any());
    }

    @Test
    void currentModeratedProjectionServesOnlyItsImmutablePublishedMedia() {
        UUID logo = UUID.fromString("3d7b60eb-e869-3184-aaea-4a9719fb2cb2");
        UUID cover = UUID.fromString("527da827-4f73-31db-8298-b31a2688772f");
        String snapshotJson = """
                {
                  "sourceSystem":"PROVIDER_PUBLIC_PROFILE_DRAFT",
                  "gallery":[{"documentId":"d746bbda-491a-32e3-b01b-9af80aec6098","caption":"Reception"}],
                  "publishedMedia":[
                    {"mediaReference":"3d7b60eb-e869-3184-aaea-4a9719fb2cb2","mediaType":"LOGO","storageKey":"immutable/logo","contentType":"image/png","originalFilename":"logo.png","displayOrder":0},
                    {"mediaReference":"527da827-4f73-31db-8298-b31a2688772f","mediaType":"COVER","storageKey":"immutable/cover","contentType":"image/png","originalFilename":"cover.png","displayOrder":1},
                    {"mediaReference":"d746bbda-491a-32e3-b01b-9af80aec6098","mediaType":"GALLERY","storageKey":"immutable/gallery","contentType":"image/png","originalFilename":"reception.png","displayOrder":2}
                  ]
                }
                """;
        DiscoverPublicProviderProfileVersionEntity currentVersion = DiscoverPublicProviderProfileVersionEntity.create(
                LEGACY_PROVIDER_ID, 21, 20, "APPROVED", "PUBLISHED", "PROVIDER_PUBLIC_PROFILE_DRAFT",
                "Published Version 20", "snapshot-hash", snapshotJson, "green-valley-family-clinic", PUBLISHED_AT
        );
        DiscoverPublicProviderProfileEntity profile = legacyProfile(currentVersion.getId(), 21, "Version 20");
        DiscoverPublicProviderProfileSlugEntity historicalAlias = DiscoverPublicProviderProfileSlugEntity.create(
                LEGACY_PROVIDER_ID, VERSION_ID, "green-valley-family-clinic", 1, true, PUBLISHED_AT.minusDays(2)
        );

        when(slugs.findFirstBySlug("green-valley-family-clinic")).thenReturn(Optional.of(historicalAlias));
        when(profiles.findByProviderId(LEGACY_PROVIDER_ID)).thenReturn(Optional.of(profile));
        when(versions.findById(currentVersion.getId())).thenReturn(Optional.of(currentVersion));
        when(storageService.getObjectBytes("immutable/logo")).thenReturn(new byte[]{1, 2});
        when(storageService.getObjectBytes("immutable/cover")).thenReturn(new byte[]{3, 4, 5});
        when(storageService.getObjectBytes("immutable/gallery")).thenReturn(new byte[]{6});

        assertThat(service.loadPublishedProviderMedia("green-valley-family-clinic", ProviderType.CLINIC,
                ProviderPublicProfileService.ProviderPublicMediaAsset.LOGO, null).orElseThrow())
                .satisfies(media -> {
                    assertThat(media.contentType()).isEqualTo("image/png");
                    assertThat(media.originalFilename()).isEqualTo("logo.png");
                    assertThat(media.bytes()).containsExactly(1, 2);
                });
        assertThat(service.loadPublishedProviderMedia("green-valley-family-clinic", ProviderType.CLINIC,
                ProviderPublicProfileService.ProviderPublicMediaAsset.COVER, null).orElseThrow().bytes())
                .containsExactly(3, 4, 5);
        assertThat(service.loadPublishedProviderMedia("green-valley-family-clinic", ProviderType.CLINIC,
                ProviderPublicProfileService.ProviderPublicMediaAsset.GALLERY, 0).orElseThrow().bytes())
                .containsExactly(6);
        assertThat(service.loadPublishedProviderMedia("green-valley-family-clinic", ProviderType.CLINIC,
                ProviderPublicProfileService.ProviderPublicMediaAsset.GALLERY, 1)).isEmpty();
        assertThat(profile.getLogoDocumentId()).isEqualTo(logo);
        assertThat(profile.getCoverImageDocumentId()).isEqualTo(cover);
        verify(documents, never()).findById(any());
    }

    private PublicProviderProfileModels.PublicProviderProfileSnapshot lifecycleSnapshot() {
        return PublicProviderProfileModels.healthcareClinicSnapshot(
                LIFECYCLE_PROVIDER_ID,
                "PROVIDER_PUBLIC_PROFILE_DRAFT",
                LIFECYCLE_PROVIDER_ID.toString(),
                "green-valley-family-clinic",
                "Green Valley Family Clinic",
                "Green Valley Family Clinic",
                "Trusted care",
                List.of("Family Medicine"),
                List.of(new PublicProviderProfileModels.PublicProviderLocationSnapshot(
                        "Green Valley Family Clinic",
                        "123 Green Valley Road",
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "Mon-Fri 09:00-20:00",
                        false,
                        false,
                        null,
                        null
                )),
                UUID.fromString("3d7b60eb-e869-3184-aaea-4a9719fb2cb2"),
                "+91 98765 02201",
                "contact@greenvalleyclinic.in",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "CALL_TO_BOOK",
                PUBLISHED_AT,
                20,
                "/discover/clinics/green-valley-family-clinic",
                1
        );
    }

    private DiscoverPublicProviderProfileEntity legacyProfile() {
        return legacyProfile(VERSION_ID, 17, "Trusted care");
    }

    private DiscoverPublicProviderProfileEntity legacyProfile(UUID versionId, int versionNumber, String summary) {
        return DiscoverPublicProviderProfileEntity.create(
                LEGACY_PROVIDER_ID,
                ProviderType.CLINIC,
                "HEALTHCARE_CLINIC",
                LEGACY_PROVIDER_ID.toString(),
                1785872344876L,
                PUBLISHED_AT,
                "green-valley-family-clinic",
                versionId,
                versionNumber,
                "Green Valley Family Clinic",
                "Green Valley Family Clinic",
                summary,
                "Family Medicine",
                "Family Medicine",
                null,
                "General Consultation",
                null,
                "Parking",
                "English",
                null,
                UUID.fromString("3d7b60eb-e869-3184-aaea-4a9719fb2cb2"),
                UUID.fromString("527da827-4f73-31db-8298-b31a2688772f"),
                null,
                "+91 98765 02201",
                "contact@greenvalleyclinic.in",
                "https://www.greenvalleyclinic.in",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "Family care",
                null,
                null,
                null,
                null,
                false,
                1,
                1,
                0,
                1,
                "CALL_TO_BOOK",
                PUBLISHED_AT
        );
    }
}
