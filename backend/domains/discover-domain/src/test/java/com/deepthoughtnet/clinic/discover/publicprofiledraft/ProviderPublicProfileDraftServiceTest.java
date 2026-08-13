package com.deepthoughtnet.clinic.discover.publicprofiledraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.onboarding.db.ProviderApplicationEntity;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.MembershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipModels.OwnershipRecord;
import com.deepthoughtnet.clinic.discover.providerownership.ProviderOwnershipService;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProfileLifecycleRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderTimingSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftSectionRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.PublicProfileDraftModels.PublicProfileDraftWorkspaceRecord;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftEntity;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftRepository;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftVersionEntity;
import com.deepthoughtnet.clinic.discover.publicprofiledraft.db.DiscoverPublicProfileDraftVersionRepository;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.db.DiscoverPublicProfileSubmissionRepository;
import com.deepthoughtnet.clinic.discover.verification.db.DiscoverProviderAccountEntity;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileMembershipRole;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileOwnershipStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderPublicProfileDraftServiceTest {
    private static final UUID PROVIDER_ACCOUNT_ID = UUID.fromString("6222eead-866b-4675-b74e-75dcd012f4f8");
    private static final String PUBLIC_PROFILE_REFERENCE = "2206731d-3f34-426f-b069-2abca255f988";
    private static final UUID PROVIDER_ID = UUID.fromString(PUBLIC_PROFILE_REFERENCE);
    private static final String CANONICAL_SLUG = "jeevanam-multispeciality-hospital";
    private static final String PUBLIC_PATH = "/discover/hospitals/jeevanam-multispeciality-hospital";
    private static final UUID LOGO_ID = UUID.fromString("3d7b60eb-e869-3184-aaea-4a9719fb2cb2");
    private static final UUID COVER_ID = UUID.fromString("527da827-4f73-31db-8298-b31a2688772f");
    private static final UUID GALLERY_ONE_ID = UUID.fromString("b5e5f0e9-5401-4d3a-90d2-5dcb2854b0f1");
    private static final UUID GALLERY_TWO_ID = UUID.fromString("b5e5f0e9-5401-4d3a-90d2-5dcb2854b0f2");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private DiscoverPublicProfileDraftRepository drafts;
    private DiscoverPublicProfileDraftVersionRepository versions;
    private ProviderOwnershipService ownershipService;
    private ClinicProfileConsentLookup clinicProfileConsentLookup;
    private ProviderPublicProfileService publicProfileService;
    private DiscoverPublicProfileSubmissionRepository submissions;
    private ObjectStorageService storageService;
    private ProviderPublicProfileDraftService service;
    private final AtomicReference<DiscoverPublicProfileDraftEntity> savedDraft = new AtomicReference<>();
    private final AtomicReference<List<DiscoverPublicProfileDraftVersionEntity>> savedVersions = new AtomicReference<>(List.of());

    @BeforeEach
    void setUp() {
        drafts = mock(DiscoverPublicProfileDraftRepository.class);
        versions = mock(DiscoverPublicProfileDraftVersionRepository.class);
        ownershipService = mock(ProviderOwnershipService.class);
        clinicProfileConsentLookup = mock(ClinicProfileConsentLookup.class);
        publicProfileService = mock(ProviderPublicProfileService.class);
        submissions = mock(DiscoverPublicProfileSubmissionRepository.class);
        storageService = mock(ObjectStorageService.class);
        service = new ProviderPublicProfileDraftService(
                drafts,
                versions,
                ownershipService,
                clinicProfileConsentLookup,
                publicProfileService,
                submissions,
                storageService,
                objectMapper
        );
        when(clinicProfileConsentLookup.findDiscoverPublicListingEnabled(PROVIDER_ID)).thenReturn(Optional.of(true));
        when(submissions.findFirstByPublicProfileReferenceAndCurrentTrueOrderBySubmittedAtDesc(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.empty());
        when(ownershipService.findOwnership(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.of(ownershipRecord()));
        when(ownershipService.listMemberships(PUBLIC_PROFILE_REFERENCE)).thenReturn(List.of(ownerMembership()));
        when(ownershipService.findLatestVerifiedOwnership(PROVIDER_ACCOUNT_ID)).thenReturn(Optional.of(ownershipRecord()));
        when(drafts.save(any())).thenAnswer(invocation -> {
            DiscoverPublicProfileDraftEntity entity = invocation.getArgument(0);
            savedDraft.set(entity);
            return entity;
        });
        when(versions.save(any())).thenAnswer(invocation -> {
            DiscoverPublicProfileDraftVersionEntity version = invocation.getArgument(0);
            List<DiscoverPublicProfileDraftVersionEntity> next = new ArrayList<>(savedVersions.get());
            next.add(version);
            savedVersions.set(next);
            return version;
        });
        when(versions.findByDraftReferenceOrderByVersionNumberDesc(any())).thenAnswer(invocation -> savedVersions.get());
    }

    @Test
    void createOrLoadDraftHydratesHospitalFromPublishedProfileAndPreservesCanonicalSlug() {
        when(drafts.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE)).thenReturn(Optional.empty());
        when(publicProfileService.findLifecycleByProviderId(PROVIDER_ID)).thenReturn(Optional.of(hospitalLifecycle()));
        when(publicProfileService.findSnapshotByProviderId(PROVIDER_ID)).thenReturn(Optional.of(hospitalSnapshot()));

        var workspace = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE);

        assertThat(workspace.publicProfileType()).isEqualTo(ProviderType.HOSPITAL);
        assertThat(workspace.publicProfileStatus()).isEqualTo("PUBLISHED");
        assertThat(workspace.canonicalSlug()).isEqualTo(CANONICAL_SLUG);
        assertThat(workspace.publicProfilePath()).isEqualTo(PUBLIC_PATH);
        assertThat(workspace.registrationNumber()).isNull();
        assertThat(workspace.completenessPercentage()).isEqualTo(100);
        assertThat(workspace.readiness().ready()).isTrue();
        assertThat(section(workspace, "about").get("description")).isEqualTo(
                "Jeevanam Multispeciality Hospital provides coordinated inpatient and outpatient care across multiple specialties."
        );
        assertThat(section(workspace, "contact").get("addressLine1")).isEqualTo("12 Green Valley Road");
        assertThat(section(workspace, "contact").get("city")).isEqualTo("Pune");
        assertThat(list(section(workspace, "services"), "items")).contains("Emergency Care");
        assertThat(list(section(workspace, "specialities"), "items")).contains("General Medicine");
        assertThat(section(workspace, "media").get("logoDocumentId")).isEqualTo(LOGO_ID.toString());
        assertThat(section(workspace, "media").get("coverDocumentId")).isEqualTo(COVER_ID.toString());
        assertThat(list(section(workspace, "media"), "gallery")).containsExactly(GALLERY_ONE_ID.toString(), GALLERY_TWO_ID.toString());
        assertThat(list(section(workspace, "timings"), "intervals")).isNotEmpty();
    }

    @Test
    void createOrLoadDraftCanonicalizesHistoricalTwentyFourSevenTimings() {
        DiscoverPublicProfileDraftEntity historicalDraft = historicalDraftEntity();
        savedDraft.set(historicalDraft);
        when(drafts.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE)).thenAnswer(invocation -> Optional.ofNullable(savedDraft.get()));
        when(publicProfileService.findLifecycleByProviderId(PROVIDER_ID)).thenReturn(Optional.of(hospitalLifecycleTwentyFourSeven()));
        when(publicProfileService.findSnapshotByProviderId(PROVIDER_ID)).thenReturn(Optional.of(hospitalSnapshotTwentyFourSeven()));

        var workspace = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE);

        assertThat(workspace.completenessPercentage()).isEqualTo(100);
        assertThat(workspace.readiness().ready()).isTrue();
        assertThat(workspace.readiness().invalidFields()).isEmpty();
        assertThat(section(workspace, "overview").get("completenessPercentage")).isEqualTo(100);
        assertThat(section(workspace, "overview").get("summaryStatus")).isEqualTo("READY");
        assertThat(section(workspace, "timings"))
                .containsEntry("timezone", "Asia/Kolkata");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intervals = (List<Map<String, Object>>) section(workspace, "timings").get("intervals");
        assertThat(intervals).containsExactly(
                sectionMap("dayOfWeek", "MONDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "TUESDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "WEDNESDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "THURSDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "FRIDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "SATURDAY", "startTime", "00:00", "endTime", "23:59"),
                sectionMap("dayOfWeek", "SUNDAY", "startTime", "00:00", "endTime", "23:59")
        );
    }

    @Test
    void createOrLoadDraftRepairsExistingHistoricalDraftWithoutOverwritingExplicitValues() {
        DiscoverPublicProfileDraftEntity historicalDraft = historicalDraftEntity();
        savedDraft.set(historicalDraft);
        when(drafts.findByPublicProfileReference(PUBLIC_PROFILE_REFERENCE)).thenAnswer(invocation -> Optional.ofNullable(savedDraft.get()));
        when(publicProfileService.findLifecycleByProviderId(PROVIDER_ID)).thenReturn(Optional.of(hospitalLifecycle()));
        when(publicProfileService.findSnapshotByProviderId(PROVIDER_ID)).thenReturn(Optional.of(hospitalSnapshot()));

        var first = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE);
        var second = service.createOrLoadDraft(PROVIDER_ACCOUNT_ID, PUBLIC_PROFILE_REFERENCE);

        assertThat(first.canonicalSlug()).isEqualTo(CANONICAL_SLUG);
        assertThat(first.publicProfilePath()).isEqualTo(PUBLIC_PATH);
        assertThat(first.registrationNumber()).isNull();
        assertThat(section(first, "about").get("shortTagline")).isEqualTo("Provider-owned banner");
        assertThat(section(first, "about").get("description")).isEqualTo(
                "Jeevanam Multispeciality Hospital provides coordinated inpatient and outpatient care across multiple specialties."
        );
        assertThat(section(first, "seo").get("slug")).isEqualTo(CANONICAL_SLUG);
        assertThat(section(first, "seo").get("canonicalPublicPath")).isEqualTo(PUBLIC_PATH);
        assertThat(section(first, "media").get("logoDocumentId")).isEqualTo(LOGO_ID.toString());
        assertThat(section(first, "media").get("coverDocumentId")).isEqualTo(COVER_ID.toString());
        assertThat(first.completenessPercentage()).isEqualTo(100);
        assertThat(second.canonicalSlug()).isEqualTo(CANONICAL_SLUG);
        assertThat(second.registrationNumber()).isNull();
        verify(drafts, times(1)).save(any());
        verify(versions, times(0)).save(any());
    }

    private static OwnershipRecord ownershipRecord() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        return new OwnershipRecord(
                UUID.fromString("4d2e12df-8b2d-4d9c-8f77-2d32f4c0b111"),
                PUBLIC_PROFILE_REFERENCE,
                PublicProfileType.HOSPITAL,
                PROVIDER_ACCOUNT_ID,
                PublicProfileOwnershipStatus.VERIFIED,
                "APPLICATION_OWNER",
                "tenant-1",
                0L,
                now,
                null,
                null,
                null,
                false,
                "Historical ownership",
                "{}",
                now,
                now
        );
    }

    private static MembershipRecord ownerMembership() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        return new MembershipRecord(
                UUID.fromString("4d2e12df-8b2d-4d9c-8f77-2d32f4c0b112"),
                PUBLIC_PROFILE_REFERENCE,
                PROVIDER_ACCOUNT_ID,
                PublicProfileMembershipRole.OWNER,
                "ACTIVE",
                0L,
                "Historical owner",
                now,
                now
        );
    }

    private static PublicProfileLifecycleRecord hospitalLifecycle() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        return new PublicProfileLifecycleRecord(
                PROVIDER_ID,
                ProviderType.HOSPITAL,
                "DISCOVER_ONBOARDING_APPLICATION",
                PUBLIC_PROFILE_REFERENCE,
                0L,
                now,
                CANONICAL_SLUG,
                "Jeevanam Multispeciality Hospital",
                "Pune",
                "Kondhwa",
                "CALL_TO_BOOK",
                "PUBLISHED",
                now,
                now,
                0L,
                PUBLIC_PATH
        );
    }

    private static PublicProviderProfileSnapshot hospitalSnapshot() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        return new PublicProviderProfileSnapshot(
                PROVIDER_ID,
                ProviderType.HOSPITAL,
                "DISCOVER_ONBOARDING_APPLICATION",
                "JHS-2026-2206731D",
                "Jeevanam Multispeciality Hospital",
                "Jeevanam Multispeciality Hospital",
                CANONICAL_SLUG,
                "Trusted multispeciality hospital in Pune",
                "Jeevanam Multispeciality Hospital provides coordinated inpatient and outpatient care across multiple specialties.",
                null,
                null,
                null,
                null,
                null,
                false,
                List.of("English", "Hindi"),
                List.of("General Medicine", "Pediatrics"),
                List.of(),
                List.of("Emergency Care", "Inpatient Care"),
                List.of("General Medicine", "Surgery"),
                List.of("ICU", "Emergency"),
                List.of("Walk-in", "Call Ahead"),
                List.of(new PublicProviderLocationSnapshot(
                        "Jeevanam Multispeciality Hospital",
                        "12 Green Valley Road",
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "24x7 access",
                        true,
                        true,
                        BigDecimal.valueOf(18.50),
                        BigDecimal.valueOf(73.80)
                )),
                List.of(
                        new PublicProviderGalleryImageSnapshot(GALLERY_ONE_ID, "front-view.jpg"),
                        new PublicProviderGalleryImageSnapshot(GALLERY_TWO_ID, "reception.jpg")
                ),
                List.of("https://cdn.example/hospital/front-view.jpg", "https://cdn.example/hospital/reception.jpg"),
                LOGO_ID,
                COVER_ID,
                null,
                "+91 98765 01502",
                "contact@jeevanam.example",
                "https://jeevanam.example",
                "Pune",
                "Kondhwa",
                "Maharashtra",
                "India",
                "General Medicine",
                "Trusted multispeciality hospital",
                "Private",
                "Multispeciality Hospital",
                "Dr Example",
                250,
                true,
                3,
                2,
                2,
                2,
                "CALL_TO_BOOK",
                true,
                now,
                1,
                PUBLIC_PATH,
                List.of(),
                List.of(
                        new PublicProviderTimingSnapshot("MONDAY", "17:00", "23:30", 0),
                        new PublicProviderTimingSnapshot("TUESDAY", "09:00", "13:00", 1)
                ),
                "Asia/Kolkata"
        );
    }

    private static PublicProfileLifecycleRecord hospitalLifecycleTwentyFourSeven() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        return new PublicProfileLifecycleRecord(
                PROVIDER_ID,
                ProviderType.HOSPITAL,
                "DISCOVER_ONBOARDING_APPLICATION",
                PUBLIC_PROFILE_REFERENCE,
                0L,
                now,
                CANONICAL_SLUG,
                "Jeevanam Multispeciality Hospital",
                "Pune",
                "Kondhwa",
                "CALL_TO_BOOK",
                "PUBLISHED",
                now,
                now,
                0L,
                PUBLIC_PATH
        );
    }

    private static PublicProviderProfileSnapshot hospitalSnapshotTwentyFourSeven() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        return new PublicProviderProfileSnapshot(
                PROVIDER_ID,
                ProviderType.HOSPITAL,
                "DISCOVER_ONBOARDING_APPLICATION",
                "JHS-2026-2206731D",
                "Jeevanam Multispeciality Hospital",
                "Jeevanam Multispeciality Hospital",
                CANONICAL_SLUG,
                "Trusted multispeciality hospital in Pune",
                "Jeevanam Multispeciality Hospital provides coordinated inpatient and outpatient care across multiple specialties.",
                null,
                null,
                null,
                null,
                null,
                false,
                List.of("English", "Hindi"),
                List.of("General Medicine", "Pediatrics"),
                List.of(),
                List.of("Emergency Care", "Inpatient Care"),
                List.of("General Medicine", "Surgery"),
                List.of("ICU", "Emergency"),
                List.of("Walk-in", "Call Ahead"),
                List.of(new PublicProviderLocationSnapshot(
                        "Jeevanam Multispeciality Hospital",
                        "12 Green Valley Road",
                        "Pune",
                        "Maharashtra",
                        "India",
                        "411001",
                        "Open 24x7",
                        true,
                        true,
                        BigDecimal.valueOf(18.50),
                        BigDecimal.valueOf(73.80)
                )),
                List.of(
                        new PublicProviderGalleryImageSnapshot(GALLERY_ONE_ID, "front-view.jpg"),
                        new PublicProviderGalleryImageSnapshot(GALLERY_TWO_ID, "reception.jpg")
                ),
                List.of("https://cdn.example/hospital/front-view.jpg", "https://cdn.example/hospital/reception.jpg"),
                LOGO_ID,
                COVER_ID,
                null,
                "+91 98765 01502",
                "contact@jeevanam.example",
                "https://jeevanam.example",
                "Pune",
                "Kondhwa",
                "Maharashtra",
                "India",
                "General Medicine",
                "Trusted multispeciality hospital",
                "Private",
                "Multispeciality Hospital",
                "Dr Example",
                250,
                true,
                3,
                2,
                2,
                2,
                "CALL_TO_BOOK",
                true,
                now,
                1,
                PUBLIC_PATH,
                List.of(),
                List.of(),
                "Asia/Kolkata"
        );
    }

    private DiscoverPublicProfileDraftEntity historicalDraftEntity() {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-11T00:00:00Z");
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("overview", sectionMap(
                "profileType", "HOSPITAL",
                "displayName", "Jeevanam Multispeciality Hospital",
                "shortTagline", "Provider-owned banner",
                "establishedYear", null,
                "summaryStatus", "DRAFT",
                "ownershipStatus", "VERIFIED",
                "tenantConsentStatus", "ENABLED",
                "contentStatus", "DRAFT_INCOMPLETE",
                "completenessPercentage", 16,
                "lastSavedAt", null
        ));
        content.put("about", sectionMap(
                "displayName", "Jeevanam Multispeciality Hospital",
                "shortTagline", "Provider-owned banner",
                "description", null,
                "philosophy", null,
                "establishedYear", null,
                "registrationNumber", PUBLIC_PROFILE_REFERENCE,
                "emergencyAvailability", null
        ));
        content.put("contact", sectionMap(
                "publicPhone", "+91 98765 01502",
                "publicEmail", "contact@jeevanam.example",
                "website", null,
                "whatsappNumber", "+91 98765 01502",
                "addressLine1", null,
                "addressLine2", null,
                "area", "Kondhwa",
                "city", "Pune",
                "state", null,
                "country", null,
                "postalCode", null,
                "phoneVisible", true,
                "emailVisible", true,
                "whatsappVisible", true
        ));
        content.put("services", sectionMap("items", List.of()));
        content.put("specialities", sectionMap("items", List.of(), "primary", null));
        content.put("facilities", sectionMap("items", List.of()));
        content.put("timings", sectionMap("timezone", "Asia/Kolkata", "weekly", List.of()));
        content.put("fees", sectionMap("currency", "INR", "visible", false));
        content.put("languages", sectionMap("items", List.of()));
        content.put("media", sectionMap(
                "logoDocumentId", null,
                "coverDocumentId", null,
                "gallery", List.of(),
                "primaryGalleryDocumentId", null,
                "galleryAltTextByDocumentId", Map.of(),
                "mediaMetadataByDocumentId", Map.of()
        ));
        content.put("seo", sectionMap(
                "slug", CANONICAL_SLUG + "-2",
                "metaTitle", "Jeevanam Multispeciality Hospital",
                "metaDescription", null,
                "canonicalPublicPath", "/discover/hospitals/" + CANONICAL_SLUG + "-2"
        ));

        return DiscoverPublicProfileDraftEntity.create(
                UUID.fromString("2fd9f851-7e78-4f49-a9d0-1e4b7ab9f001"),
                UUID.randomUUID().toString(),
                PUBLIC_PROFILE_REFERENCE,
                ProviderType.HOSPITAL,
                PROVIDER_ACCOUNT_ID,
                "VERIFIED",
                "ENABLED",
                "PUBLISHED",
                "DRAFT_INCOMPLETE",
                "INCOMPLETE",
                16,
                1,
                "HEALTHCARE_CLINIC_PROFILE",
                PUBLIC_PROFILE_REFERENCE,
                0L,
                now,
                "Jeevanam Multispeciality Hospital",
                CANONICAL_SLUG + "-2",
                "Pune",
                "Kondhwa",
                "Maharashtra",
                "India",
                "+91 98765 01502",
                "contact@jeevanam.example",
                "https://jeevanam.example",
                "+91 98765 01502",
                PUBLIC_PROFILE_REFERENCE,
                null,
                now,
                PROVIDER_ACCOUNT_ID,
                PROVIDER_ACCOUNT_ID,
                now,
                now,
                "/discover/hospitals/" + CANONICAL_SLUG + "-2",
                toJson(content),
                toJson(Map.of()),
                toJson(Map.of())
        );
    }

    private Map<String, Object> sectionMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(PublicProfileDraftWorkspaceRecord workspace, String key) {
        return workspace.sections().stream()
                .filter(section -> key.equals(section.key()))
                .findFirst()
                .map(PublicProfileDraftSectionRecord::content)
                .orElse(Map.of());
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Map<String, Object> section, String key) {
        Object value = section.get(key);
        if (value instanceof List<?> values) {
            return values.stream().map(item -> String.valueOf(item)).toList();
        }
        return List.of();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
