package com.deepthoughtnet.clinic.api.publicsite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.PublicDoctorPracticeAssociationService;
import com.deepthoughtnet.clinic.discover.publichospitaldoctorassociation.PublicHospitalDoctorAssociationService;
import com.deepthoughtnet.clinic.discover.publicdoctorpracticeassociation.db.DiscoverPublicDoctorPracticeAssociationEntity;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicSpecialitySummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderSearchCriteria;
import com.deepthoughtnet.clinic.discover.publicprofilemoderation.ProviderPublicProfileModerationService;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.providerintegration.service.ProviderLinkingService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PublicCatalogFacadeTest {

    private static ProviderPublicProfileModerationService moderationService() {
        return mock(ProviderPublicProfileModerationService.class);
    }

    @Test
    void mapsPublishedProfilesIntoPublicListsAndSearchResults() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = facade(publicProfileService);

        var doctor = summaryRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "dr-asha-menon",
                "/discover/doctors/dr-asha-menon",
                "Dr. Asha Menon",
                "Experienced public doctor",
                "Dermatology",
                "Pune",
                "Baner",
                "https://example.com/doctor.jpg",
                "https://example.com/doctor-cover.jpg",
                1,
                2,
                3,
                4,
                "+911111111111",
                "ONLINE_BOOKING",
                false,
                List.of("General Physician")
        );
        var clinic = summaryRecord(
                ProviderType.CLINIC,
                "sunrise-clinic",
                "/discover/clinics/sunrise-clinic",
                "Sunrise Clinic",
                "Clinic summary",
                "Dermatology",
                "Pune",
                "Baner",
                "https://example.com/clinic.jpg",
                "https://example.com/clinic-cover.jpg",
                5,
                6,
                7,
                8,
                "+912222222222",
                "CALL_TO_BOOK",
                false,
                List.of("Dermatology", "Pediatrics")
        );
        var hospital = summaryRecord(
                ProviderType.HOSPITAL,
                "city-care-hospital",
                "/discover/hospitals/city-care-hospital",
                "City Care Hospital",
                "Hospital summary",
                "Cardiology",
                "Pune",
                "Kalyani Nagar",
                "https://example.com/hospital.jpg",
                "https://example.com/hospital-cover.jpg",
                9,
                10,
                11,
                12,
                "+913333333333",
                "CALL_TO_BOOK",
                true,
                List.of("Cardiology")
        );

        when(publicProfileService.listProfiles(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            PublicProviderSearchCriteria criteria = invocation.getArgument(0);
            return switch (criteria.providerType()) {
                case INDIVIDUAL_DOCTOR -> new PageImpl<>(List.of(doctor), PageRequest.of(0, 12), 1);
                case CLINIC -> new PageImpl<>(List.of(clinic), PageRequest.of(0, 12), 1);
                case HOSPITAL -> new PageImpl<>(List.of(hospital), PageRequest.of(0, 12), 1);
            };
        });
        when(publicProfileService.listSpecialities("skin", "pune")).thenReturn(List.of(
                new PublicSpecialitySummaryRecord("Dermatology", "dermatology", 3, 2, 1)
        ));

        var doctors = facade.listDoctors("skin", "pune", "baner", "Dermatology", "sunrise", "demo", null, null, null, 0, 12);
        var clinics = facade.listClinics("skin", "pune", "baner", "Dermatology", "sunrise", null, null, null, 0, 12);
        var hospitals = facade.listHospitals("skin", "pune", "baner", "Dermatology", "sunrise", null, null, null, 0, 12);
        PublicSearchResponse search = facade.search("skin", "pune", "baner", "sunrise", null, null, null, 0, 6);

        assertThat(doctors.items()).singleElement().satisfies(item -> {
            assertThat(item.doctorDisplayName()).isEqualTo("Dr. Asha Menon");
            assertThat(item.publicPath()).isEqualTo("/discover/doctors/dr-asha-menon");
            assertThat(item.photoUrl()).isEqualTo("/api/public/doctors/dr-asha-menon/photo");
            assertThat(item.subtitle()).isEqualTo("Dr. Asha Menon subtitle");
            assertThat(item.summary()).isEqualTo("Experienced public doctor");
        });
        assertThat(clinics.items()).singleElement().satisfies(item -> {
            assertThat(item.clinicDisplayName()).isEqualTo("Sunrise Clinic");
            assertThat(item.publicPath()).isEqualTo("/discover/clinics/sunrise-clinic");
            assertThat(item.logoUrl()).isEqualTo("/api/public/clinics/sunrise-clinic/logo");
            assertThat(item.coverUrl()).isEqualTo("/api/public/clinics/sunrise-clinic/cover");
            assertThat(item.emergencyAvailable()).isFalse();
        });
        assertThat(hospitals.items()).singleElement().satisfies(item -> {
            assertThat(item.hospitalDisplayName()).isEqualTo("City Care Hospital");
            assertThat(item.publicPath()).isEqualTo("/discover/hospitals/city-care-hospital");
            assertThat(item.logoUrl()).isEqualTo("/api/public/hospitals/city-care-hospital/logo");
            assertThat(item.coverUrl()).isEqualTo("/api/public/hospitals/city-care-hospital/cover");
            assertThat(item.emergencyAvailable()).isTrue();
            assertThat(item.bookingMode()).isEqualTo("CALL_TO_BOOK");
        });
        assertThat(search.hospitals().items()).singleElement().satisfies(item -> assertThat(item.hospitalDisplayName()).isEqualTo("City Care Hospital"));
        assertThat(facade.listSpecialities("skin", "pune", "demo")).extracting("speciality").containsExactly("Dermatology");
    }

    @Test
    void clinicDetailUsesPublicMediaRoutesForPublishedAssets() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = facade(publicProfileService);

        when(publicProfileService.findBySlug("sunrise-clinic")).thenReturn(Optional.of(
                detailRecord(
                        ProviderType.CLINIC,
                        "JCL-0001",
                        "sunrise-clinic",
                        "/discover/clinics/sunrise-clinic",
                        "Sunrise Clinic",
                        "Sunrise Clinic",
                        "Clinic summary",
                        "Clinic description",
                        "Clinic biography",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of(),
                        List.of("General Medicine"),
                        List.of(),
                        List.of("Consultation"),
                        List.of("Outpatient"),
                        List.of("Wheelchair Access"),
                        List.of("In-person"),
                        List.of(new PublicProviderLocationSnapshot("Primary", "Main Road", "Pune", "Maharashtra", "India", "411001", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                        List.of(
                                new PublicProviderGalleryImageSnapshot(UUID.randomUUID(), "gallery-one.png"),
                                new PublicProviderGalleryImageSnapshot(UUID.randomUUID(), "gallery-two.png")
                        ),
                        List.of("https://example.com/gallery-one.png", "https://example.com/gallery-two.png"),
                        "https://example.com/clinic-image.png",
                        "https://example.com/clinic-cover.png",
                        "https://example.com/clinic-logo.png",
                        "9876543210",
                        "clinic@example.com",
                        "https://example.com",
                        "Pune",
                        "Main Road",
                        "Maharashtra",
                        "India",
                        "General Medicine",
                        "Private",
                        null,
                        null,
                        null,
                        false,
                        "CALL_TO_BOOK",
                        false,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                        1,
                        "sunrise-clinic",
                        null,
                        true
                )
        ));

        var detail = facade.clinicDetail("sunrise-clinic");

        assertThat(detail.logoUrl()).isEqualTo("/api/public/clinics/sunrise-clinic/logo");
        assertThat(detail.coverUrl()).isEqualTo("/api/public/clinics/sunrise-clinic/cover");
        assertThat(detail.galleryImageUrls()).containsExactly(
                "/api/public/clinics/sunrise-clinic/gallery/0",
                "/api/public/clinics/sunrise-clinic/gallery/1"
        );
        assertThat(detail.timings()).containsExactly("MONDAY 09:00-17:00");
        assertThat(detail.doctors()).isEmpty();
    }

    @Test
    void clinicDetailIncludesAssociatedDoctors() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, mock(ProviderLinkingService.class), moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));

        PublicProviderProfileDetailRecord clinicDetail = detailRecord(
                ProviderType.CLINIC,
                "JCL-0001",
                "sunrise-clinic",
                "/discover/clinics/sunrise-clinic",
                "Sunrise Clinic",
                "Sunrise Clinic",
                "Clinic summary",
                "Clinic description",
                "Clinic biography",
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of("Outpatient"),
                List.of("Wheelchair Access"),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Primary", "Main Road", "Pune", "Maharashtra", "India", "411001", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "9876543210",
                "clinic@example.com",
                "https://example.com",
                "Pune",
                "Main Road",
                "Maharashtra",
                "India",
                "General Medicine",
                "Private",
                null,
                null,
                null,
                false,
                "CALL_TO_BOOK",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "sunrise-clinic",
                null,
                true
        );
        PublicProviderProfileDetailRecord doctorDetail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "JDN-0001",
                "dr-asha-menon",
                "/discover/doctors/dr-asha-menon",
                "Dr. Asha Menon",
                "Dr. Asha Menon",
                "Experienced public doctor",
                "Doctor summary",
                "Professional biography",
                "MBBS",
                "MCI",
                8,
                new BigDecimal("500"),
                15,
                true,
                List.of("English", "Hindi"),
                List.of("Dermatology"),
                List.of("Skin Care"),
                List.of("Consultations"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Sunrise Clinic", "Main Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/doctor.jpg",
                null,
                null,
                "1234567890",
                "doctor@example.com",
                "https://example.com",
                "Pune",
                "Baner",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                true,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "dr-asha-menon",
                null,
                true
        );

        when(publicProfileService.findBySlug("sunrise-clinic")).thenReturn(Optional.of(clinicDetail));
        when(associationService.listPublishedDoctorReferencesByPractice(clinicDetail.providerId())).thenReturn(List.of(doctorDetail.providerId()));
        when(publicProfileService.findByProviderId(doctorDetail.providerId())).thenReturn(Optional.of(doctorDetail));

        var detail = facade.clinicDetail("sunrise-clinic");

        assertThat(detail.doctors()).singleElement().satisfies(doctor -> {
            assertThat(doctor.doctorDisplayName()).isEqualTo("Dr. Asha Menon");
            assertThat(doctor.clinicDisplayName()).isEqualTo("Sunrise Clinic");
            assertThat(doctor.clinicSlug()).isEqualTo("sunrise-clinic");
            assertThat(doctor.publicPath()).isEqualTo("/discover/doctors/dr-asha-menon");
        });
    }

    @Test
    void hospitalDetailUsesContactOnlyBookingAndExplicitHospitalAssociations() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicHospitalDoctorAssociationService hospitalAssociationService = mock(PublicHospitalDoctorAssociationService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, mock(ProviderLinkingService.class), moderationService(), mock(PublicDoctorPracticeAssociationService.class), hospitalAssociationService);

        PublicProviderProfileDetailRecord hospitalDetail = detailRecord(
                ProviderType.HOSPITAL,
                "HSP-0001",
                "jeevanam-multispeciality-hospital",
                "/discover/hospitals/jeevanam-multispeciality-hospital",
                "Jeevanam Multispeciality Hospital",
                "Jeevanam Multispeciality Hospital",
                "Hospital summary",
                "Hospital description",
                "Hospital biography",
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(),
                List.of("General Medicine"),
                List.of(),
                List.of("Emergency"),
                List.of("Inpatient"),
                List.of("Lift access"),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Main", "Main Road", "Pune", "Maharashtra", "India", "411001", "Mon-Sun 9 AM-9 PM", true, true, null, null)),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "9876543210",
                "hospital@example.com",
                "https://example.com",
                "Pune",
                "Main Road",
                "Maharashtra",
                "India",
                "General Medicine",
                "Private",
                null,
                null,
                null,
                true,
                "CALL_TO_BOOK",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "jeevanam-multispeciality-hospital",
                null,
                true
        );
        PublicProviderProfileDetailRecord doctorDetail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "9c6cbf9a-9f14-4f58-8d2c-4cb7c6f3f001",
                "dr-neha-sharma",
                "/discover/doctors/dr-neha-sharma",
                "Dr. Neha Sharma",
                "Dr. Neha Sharma",
                "General Medicine",
                "Doctor summary",
                "Doctor biography",
                "MBBS, MD",
                "MMC",
                11,
                new BigDecimal("700"),
                15,
                true,
                List.of("English"),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Jeevanam Multispeciality Hospital", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/doctor.png",
                null,
                null,
                "+911111111111",
                "doctor@example.com",
                "https://example.com",
                "Pune",
                "Main Road",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "dr-neha-sharma",
                null,
                true
        );

        when(publicProfileService.findBySlug("jeevanam-multispeciality-hospital")).thenReturn(Optional.of(hospitalDetail));
        when(hospitalAssociationService.listPublishedDoctorReferencesByHospital(hospitalDetail.providerId())).thenReturn(List.of(doctorDetail.providerId()));
        when(publicProfileService.findByProviderId(doctorDetail.providerId())).thenReturn(Optional.of(doctorDetail));

        var detail = facade.hospitalDetail("jeevanam-multispeciality-hospital");

        assertThat(detail.bookingMode()).isEqualTo("CALL_TO_BOOK");
        assertThat(detail.doctors()).singleElement().satisfies(doctor -> {
            assertThat(doctor.publicDoctorId()).isEqualTo(doctorDetail.providerId().toString());
            assertThat(doctor.doctorDisplayName()).isEqualTo("Dr. Neha Sharma");
            assertThat(doctor.clinicDisplayName()).isEqualTo("Jeevanam Multispeciality Hospital");
            assertThat(doctor.clinicSlug()).isEqualTo("jeevanam-multispeciality-hospital");
            assertThat(doctor.bookingMode()).isEqualTo("NOT_AVAILABLE");
        });
    }

    @Test
    void clinicSummaryAndDetailResolveOnlineBookingWhenAnyAssociatedDoctorCanBookOnline() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        ProviderLinkingService providerLinkingService = mock(ProviderLinkingService.class);
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, providerLinkingService, moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));

        PublicProviderProfileDetailRecord clinicDetail = detailRecord(
                ProviderType.CLINIC,
                "CL-ONLINE",
                "green-valley-family-clinic",
                "/discover/clinics/green-valley-family-clinic",
                "Green Valley Family Clinic",
                "Green Valley Family Clinic",
                "Clinic subtitle",
                "Clinic summary",
                "Clinic biography",
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of("Outpatient"),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Primary", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "9876543210",
                "clinic@example.com",
                "https://example.com",
                "Pune",
                "Main Road",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "CALL_TO_BOOK",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "green-valley-family-clinic",
                null,
                true
        );
        PublicProviderProfileSummaryRecord clinicSummary = new PublicProviderProfileSummaryRecord(
                clinicDetail.providerId(),
                ProviderType.CLINIC,
                clinicDetail.canonicalSlug(),
                clinicDetail.publicPath(),
                clinicDetail.displayName(),
                clinicDetail.subtitle(),
                clinicDetail.summary(),
                clinicDetail.primarySpeciality(),
                clinicDetail.city(),
                clinicDetail.area(),
                clinicDetail.imageUrl(),
                clinicDetail.coverUrl(),
                1,
                2,
                3,
                4,
                clinicDetail.contactPhone(),
                "CALL_TO_BOOK",
                false,
                List.of("General Medicine"),
                null
        );
        PublicProviderProfileDetailRecord doctorDetail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "DR-ONLINE",
                "amit-verma-2",
                "/discover/doctors/amit-verma-2",
                "Amit Verma",
                "Amit Verma",
                "General Medicine",
                "Doctor summary",
                "Doctor biography",
                "MBBS, MD",
                "MMC",
                12,
                new BigDecimal("800"),
                15,
                true,
                List.of("English"),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Green Valley Family Clinic", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/doctor.png",
                null,
                null,
                "+911111111111",
                "doctor@example.com",
                "https://example.com",
                "Pune",
                "Green Valley",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "amit-verma-2",
                null,
                true
        );

        when(publicProfileService.findBySlug("green-valley-family-clinic")).thenReturn(Optional.of(clinicDetail));
        when(publicProfileService.listProfiles(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(clinicSummary), PageRequest.of(0, 12), 1));
        when(associationService.listPublishedDoctorReferencesByPractice(clinicDetail.providerId())).thenReturn(List.of(doctorDetail.providerId()));
        when(publicProfileService.findByProviderId(doctorDetail.providerId())).thenReturn(Optional.of(doctorDetail));
        when(providerLinkingService.resolveBookingTarget(new PublicProviderReference(doctorDetail.providerId().toString(), clinicDetail.providerId().toString()))).thenReturn(Optional.of(
                new BookingTargetResolution(
                        new BookingTargetReference("opaque-booking-reference", 11L),
                        new ProviderSourceReference(SourceSystem.HEALTHCARE_DOCTOR, doctorDetail.referenceNumber(), 8L, OffsetDateTime.parse("2026-01-01T10:00:00Z")),
                        PublicProfileType.DOCTOR,
                        new PublicProviderReference(doctorDetail.providerId().toString(), clinicDetail.providerId().toString()),
                        "tenant-1",
                        "platform-clinic-1",
                        "tenant-doctor-user-1",
                        "tenant-doctor-profile-1",
                        BookingCapability.ONLINE_BOOKING,
                        AvailabilityState.AVAILABLE_TODAY,
                        PlatformConnectionStatus.CONNECTED,
                        LinkLifecycleStatus.LINKED,
                        11L,
                        22L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z")
                )
        ));

        var clinicList = facade.listClinics(null, null, null, null, null, null, null, null, 0, 12);
        var clinicDetailResponse = facade.clinicDetail("green-valley-family-clinic");

        assertThat(clinicList.items()).singleElement().satisfies(item -> assertThat(item.bookingMode()).isEqualTo("ONLINE_BOOKING"));
        assertThat(clinicDetailResponse.bookingMode()).isEqualTo("ONLINE_BOOKING");
        assertThat(clinicDetailResponse.doctors()).singleElement().satisfies(doctor -> {
            assertThat(doctor.publicDoctorId()).isEqualTo(doctorDetail.providerId().toString());
            assertThat(doctor.clinicDisplayName()).isEqualTo("Green Valley Family Clinic");
            assertThat(doctor.clinicSlug()).isEqualTo("green-valley-family-clinic");
            assertThat(doctor.bookingMode()).isEqualTo("ONLINE_BOOKING");
        });
    }

    @Test
    void doctorDetailIncludesOpaqueBookingReferenceWhenPlatformLinkExists() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        ProviderLinkingService providerLinkingService = mock(ProviderLinkingService.class);
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, providerLinkingService, moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));

        PublicProviderProfileDetailRecord detail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "DR-0007",
                "dr-asha-menon",
                "/discover/doctors/dr-asha-menon",
                "Dr. Asha Menon",
                "Dr. Asha Menon",
                "Experienced doctor",
                "Doctor summary",
                "Doctor biography",
                "MBBS",
                "MMC",
                8,
                new BigDecimal("800"),
                15,
                true,
                List.of("English"),
                List.of("Dermatology"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Primary Clinic", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/doctor.png",
                null,
                null,
                "+911111111111",
                "doctor@example.com",
                "https://example.com",
                "Pune",
                "Baner",
                "Maharashtra",
                "India",
                "Dermatology",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "dr-asha-menon",
                null,
                true
        );
        String practiceReference = UUID.nameUUIDFromBytes(
                String.join("|",
                        detail.providerId().toString(),
                        "0",
                        "Primary Clinic",
                        "Main Road",
                        "Pune"
                ).getBytes(StandardCharsets.UTF_8)
        ).toString();
        when(publicProfileService.findBySlug("dr-asha-menon")).thenReturn(Optional.of(detail));
        when(providerLinkingService.resolveBookingTarget(new PublicProviderReference(detail.providerId().toString(), practiceReference))).thenReturn(Optional.of(
                new BookingTargetResolution(
                        new BookingTargetReference("opaque-booking-reference", 11L),
                        new ProviderSourceReference(SourceSystem.HEALTHCARE_DOCTOR, detail.referenceNumber(), 8L, OffsetDateTime.parse("2026-01-01T10:00:00Z")),
                        PublicProfileType.DOCTOR,
                        new PublicProviderReference(detail.providerId().toString(), practiceReference),
                        "tenant-1",
                        "platform-clinic-1",
                        "tenant-doctor-user-1",
                        "tenant-doctor-profile-1",
                        BookingCapability.ONLINE_BOOKING,
                        AvailabilityState.AVAILABLE_TODAY,
                        PlatformConnectionStatus.CONNECTED,
                        LinkLifecycleStatus.LINKED,
                        11L,
                        22L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z")
                )
        ));

        var doctor = facade.doctorDetail("dr-asha-menon");

        assertThat(doctor.bookingReference()).isEqualTo("opaque-booking-reference");
        assertThat(doctor.clinics()).singleElement().satisfies(clinic -> assertThat(clinic.bookingReference()).isEqualTo("opaque-booking-reference"));
        assertThat(doctor.availableToday()).isTrue();
    }

    @Test
    void doctorSummaryAndDetailResolveBookingModeFromSingleAssociatedPractice() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        ProviderLinkingService providerLinkingService = mock(ProviderLinkingService.class);
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, providerLinkingService, moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));

        PublicProviderProfileDetailRecord detail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "DR-ONLINE",
                "dr-asha-menon",
                "/discover/doctors/dr-asha-menon",
                "Dr. Asha Menon",
                "Dr. Asha Menon",
                "Experienced doctor",
                "Doctor summary",
                "Doctor biography",
                "MBBS",
                "MMC",
                8,
                new BigDecimal("800"),
                15,
                true,
                List.of("English"),
                List.of("Dermatology"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Green Valley Family Clinic", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/doctor.png",
                null,
                null,
                "+911111111111",
                "doctor@example.com",
                "https://example.com",
                "Pune",
                "Baner",
                "Maharashtra",
                "India",
                "Dermatology",
                null,
                null,
                null,
                null,
                false,
                "CALL_TO_BOOK",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "dr-asha-menon",
                null,
                true
        );
        PublicProviderProfileSummaryRecord summary = new PublicProviderProfileSummaryRecord(
                detail.providerId(),
                ProviderType.INDIVIDUAL_DOCTOR,
                detail.canonicalSlug(),
                detail.publicPath(),
                detail.displayName(),
                detail.subtitle(),
                detail.summary(),
                detail.primarySpeciality(),
                detail.city(),
                detail.area(),
                detail.imageUrl(),
                detail.coverUrl(),
                1,
                2,
                3,
                4,
                detail.contactPhone(),
                "CALL_TO_BOOK",
                false,
                List.of("Dermatology"),
                null
        );
        UUID practiceId = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");

        when(publicProfileService.findBySlug("dr-asha-menon")).thenReturn(Optional.of(detail));
        when(publicProfileService.listProfiles(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 12), 1));
        when(associationService.listPublishedPracticeReferencesByDoctor(detail.providerId())).thenReturn(List.of(practiceId));
        when(providerLinkingService.resolveBookingTarget(new PublicProviderReference(detail.providerId().toString(), practiceId.toString()))).thenReturn(Optional.of(
                new BookingTargetResolution(
                        new BookingTargetReference("opaque-booking-reference", 11L),
                        new ProviderSourceReference(SourceSystem.HEALTHCARE_DOCTOR, detail.referenceNumber(), 8L, OffsetDateTime.parse("2026-01-01T10:00:00Z")),
                        PublicProfileType.DOCTOR,
                        new PublicProviderReference(detail.providerId().toString(), practiceId.toString()),
                        "tenant-1",
                        practiceId.toString(),
                        "tenant-doctor-user-1",
                        "tenant-doctor-profile-1",
                        BookingCapability.ONLINE_BOOKING,
                        AvailabilityState.AVAILABLE_TODAY,
                        PlatformConnectionStatus.CONNECTED,
                        LinkLifecycleStatus.LINKED,
                        11L,
                        22L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z")
                )
        ));

        assertThat(facade.doctorDetail("dr-asha-menon").bookingMode()).isEqualTo("ONLINE_BOOKING");
        assertThat(facade.doctorDetail("dr-asha-menon").canBookOnline()).isTrue();
        assertThat(facade.listDoctors("Asha", "Pune", "Baner", "Dermatology", null, "demo", null, null, null, 0, 12)
                .items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.bookingMode()).isEqualTo("ONLINE_BOOKING");
                    assertThat(item.canBookOnline()).isTrue();
                });
    }

    @Test
    void doctorSummaryAndDetailDowngradeToCallToBookWhenNoOnlineBookingTargetExists() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = facade(publicProfileService);

        PublicProviderProfileDetailRecord detail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "JDN-0002",
                "dr-booking-pending",
                "/discover/doctors/dr-booking-pending",
                "Dr. Booking Pending",
                "Dr. Booking Pending",
                "Public doctor summary",
                "Doctor summary",
                "Doctor biography",
                "MBBS",
                "MMC",
                9,
                new BigDecimal("800"),
                15,
                true,
                List.of("English"),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/doctor.png",
                null,
                null,
                "+911234567890",
                "doctor@example.com",
                "https://example.com",
                "Pune",
                "Baner",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "dr-booking-pending",
                null,
                true
        );
        PublicProviderProfileSummaryRecord summary = new PublicProviderProfileSummaryRecord(
                detail.providerId(),
                ProviderType.INDIVIDUAL_DOCTOR,
                detail.canonicalSlug(),
                detail.publicPath(),
                detail.displayName(),
                detail.subtitle(),
                detail.summary(),
                detail.primarySpeciality(),
                detail.city(),
                detail.area(),
                detail.imageUrl(),
                detail.coverUrl(),
                1,
                2,
                3,
                4,
                detail.contactPhone(),
                "ONLINE_BOOKING",
                false,
                List.of("General Medicine"),
                null
        );

        when(publicProfileService.findBySlug("dr-booking-pending")).thenReturn(Optional.of(detail));
        when(publicProfileService.listProfiles(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 12), 1));

        var detailResponse = facade.doctorDetail("dr-booking-pending");
        var listResponse = facade.listDoctors("Booking", "Pune", "Baner", "General Medicine", null, "demo", null, null, null, 0, 12);

        assertThat(detailResponse.bookingMode()).isEqualTo("CALL_TO_BOOK");
        assertThat(detailResponse.canBookOnline()).isFalse();
        assertThat(listResponse.items()).singleElement().satisfies(item -> {
            assertThat(item.bookingMode()).isEqualTo("CALL_TO_BOOK");
            assertThat(item.canBookOnline()).isFalse();
        });
    }

    @Test
    void doctorSummaryUsesAvailableTodayOnlyWhenLinkedAvailabilityStateIsAvailableToday() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        ProviderLinkingService providerLinkingService = mock(ProviderLinkingService.class);
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, providerLinkingService, moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));

        PublicProviderProfileDetailRecord detail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "DR-AVAIL-1",
                "neeraj-kulkarni",
                "/discover/doctors/neeraj-kulkarni",
                "Neeraj Kulkarni",
                "Neeraj Kulkarni",
                "Public doctor summary",
                "Doctor summary",
                "Doctor biography",
                "MBBS",
                "MMC",
                10,
                new BigDecimal("700"),
                15,
                true,
                List.of("English"),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Green Valley Family Clinic", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/neeraj.png",
                null,
                null,
                "+911111111111",
                "neeraj@example.com",
                "https://example.com",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "neeraj-kulkarni",
                null,
                true
        );
        PublicProviderProfileSummaryRecord summary = new PublicProviderProfileSummaryRecord(
                detail.providerId(),
                ProviderType.INDIVIDUAL_DOCTOR,
                "neeraj-kulkarni",
                "/discover/doctors/neeraj-kulkarni",
                "Neeraj Kulkarni",
                "Neeraj Kulkarni subtitle",
                "Public doctor summary",
                "General Medicine",
                "Pune",
                "Wakad",
                "https://example.com/neeraj.png",
                null,
                1,
                1,
                1,
                0,
                "+911111111111",
                "ONLINE_BOOKING",
                false,
                List.of("General Medicine"),
                null
        );
        UUID practiceId = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");

        when(publicProfileService.findBySlug("neeraj-kulkarni")).thenReturn(Optional.of(detail));
        when(publicProfileService.listProfiles(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 12), 1));
        when(associationService.listPublishedPracticeReferencesByDoctor(detail.providerId())).thenReturn(List.of(practiceId));
        when(providerLinkingService.resolveBookingTarget(new PublicProviderReference(detail.providerId().toString(), practiceId.toString()))).thenReturn(Optional.of(
                new BookingTargetResolution(
                        new BookingTargetReference("opaque-booking-reference", 11L),
                        new ProviderSourceReference(SourceSystem.HEALTHCARE_DOCTOR, detail.referenceNumber(), 8L, OffsetDateTime.parse("2026-01-01T10:00:00Z")),
                        PublicProfileType.DOCTOR,
                        new PublicProviderReference(detail.providerId().toString(), practiceId.toString()),
                        "tenant-1",
                        practiceId.toString(),
                        "tenant-doctor-user-1",
                        "tenant-doctor-profile-1",
                        BookingCapability.ONLINE_BOOKING,
                        AvailabilityState.AVAILABLE_TODAY,
                        PlatformConnectionStatus.CONNECTED,
                        LinkLifecycleStatus.LINKED,
                        11L,
                        22L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z")
                )
        ));

        assertThat(facade.listDoctors("Neeraj", "Pune", "Wakad", "General Medicine", null, "demo", null, null, null, 0, 12)
                .items())
                .singleElement()
                .satisfies(item -> assertThat(item.availableToday()).isTrue());
    }

    @Test
    void doctorSummaryDoesNotInferAvailableTodayFromOnlineBookingWithoutAvailabilitySignal() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        ProviderLinkingService providerLinkingService = mock(ProviderLinkingService.class);
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, providerLinkingService, moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));

        PublicProviderProfileDetailRecord detail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "DR-AVAIL-2",
                "neeraj-kulkarni",
                "/discover/doctors/neeraj-kulkarni",
                "Neeraj Kulkarni",
                "Neeraj Kulkarni",
                "Public doctor summary",
                "Doctor summary",
                "Doctor biography",
                "MBBS",
                "MMC",
                10,
                new BigDecimal("700"),
                15,
                true,
                List.of("English"),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Green Valley Family Clinic", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/neeraj.png",
                null,
                null,
                "+911111111111",
                "neeraj@example.com",
                "https://example.com",
                "Pune",
                "Wakad",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "neeraj-kulkarni",
                null,
                true
        );
        PublicProviderProfileSummaryRecord summary = new PublicProviderProfileSummaryRecord(
                detail.providerId(),
                ProviderType.INDIVIDUAL_DOCTOR,
                "neeraj-kulkarni",
                "/discover/doctors/neeraj-kulkarni",
                "Neeraj Kulkarni",
                "Neeraj Kulkarni subtitle",
                "Public doctor summary",
                "General Medicine",
                "Pune",
                "Wakad",
                "https://example.com/neeraj.png",
                null,
                1,
                1,
                1,
                0,
                "+911111111111",
                "ONLINE_BOOKING",
                false,
                List.of("General Medicine"),
                null
        );
        UUID practiceId = UUID.fromString("fb6977b3-683b-40a3-95b8-05ffbad1dac0");

        when(publicProfileService.findBySlug("neeraj-kulkarni")).thenReturn(Optional.of(detail));
        when(publicProfileService.listProfiles(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 12), 1));
        when(associationService.listPublishedPracticeReferencesByDoctor(detail.providerId())).thenReturn(List.of(practiceId));
        when(providerLinkingService.resolveBookingTarget(new PublicProviderReference(detail.providerId().toString(), practiceId.toString()))).thenReturn(Optional.of(
                new BookingTargetResolution(
                        new BookingTargetReference("opaque-booking-reference", 11L),
                        new ProviderSourceReference(SourceSystem.HEALTHCARE_DOCTOR, detail.referenceNumber(), 8L, OffsetDateTime.parse("2026-01-01T10:00:00Z")),
                        PublicProfileType.DOCTOR,
                        new PublicProviderReference(detail.providerId().toString(), practiceId.toString()),
                        "tenant-1",
                        practiceId.toString(),
                        "tenant-doctor-user-1",
                        "tenant-doctor-profile-1",
                        BookingCapability.ONLINE_BOOKING,
                        AvailabilityState.UNKNOWN,
                        PlatformConnectionStatus.CONNECTED,
                        LinkLifecycleStatus.LINKED,
                        11L,
                        22L,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z")
                )
        ));

        assertThat(facade.listDoctors("Neeraj", "Pune", "Wakad", "General Medicine", null, "demo", null, null, null, 0, 12)
                .items())
                .singleElement()
                .satisfies(item -> assertThat(item.availableToday()).isFalse());
    }

    @Test
    void doctorDetailIncludesMultiplePracticesAndKeepsClinicCompatibilityFields() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        when(associationService.listPublishedDoctorReferencesByPractice(any())).thenReturn(List.of());
        when(associationService.listPublishedPracticeReferencesByDoctor(any())).thenReturn(List.of());
        when(associationService.findActiveAssociationsByPublicDoctorReference(any())).thenReturn(List.of(mock(DiscoverPublicDoctorPracticeAssociationEntity.class)));
        when(associationService.findActiveAssociationsByPublicPracticeReference(any())).thenReturn(List.of());
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService, mock(ProviderLinkingService.class), moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));

        PublicProviderProfileDetailRecord doctorDetail = detailRecord(
                ProviderType.INDIVIDUAL_DOCTOR,
                "DR-0008",
                "dr-asha-menon",
                "/discover/doctors/dr-asha-menon",
                "Dr. Asha Menon",
                "Dr. Asha Menon",
                "Experienced doctor",
                "Doctor summary",
                "Doctor biography",
                "MBBS",
                "MMC",
                8,
                new BigDecimal("800"),
                15,
                true,
                List.of("English"),
                List.of("Dermatology"),
                List.of(),
                List.of("Consultation"),
                List.of(),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Primary Clinic", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                "https://example.com/doctor.png",
                null,
                null,
                "+911111111111",
                "doctor@example.com",
                "https://example.com",
                "Pune",
                "Baner",
                "Maharashtra",
                "India",
                "Dermatology",
                null,
                null,
                null,
                null,
                false,
                "ONLINE_BOOKING",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "dr-asha-menon",
                null,
                true
        );
        PublicProviderProfileDetailRecord clinicDetail = detailRecord(
                ProviderType.CLINIC,
                "CL-0001",
                "sunrise-clinic",
                "/discover/clinics/sunrise-clinic",
                "Sunrise Clinic",
                "Sunrise Clinic",
                "Clinic subtitle",
                "Clinic summary",
                "Clinic biography",
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(),
                List.of("General Medicine"),
                List.of(),
                List.of("Consultation"),
                List.of("Outpatient"),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Primary", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "9876543210",
                "clinic@example.com",
                "https://example.com",
                "Pune",
                "Main Road",
                "Maharashtra",
                "India",
                "General Medicine",
                null,
                null,
                null,
                null,
                false,
                "CALL_TO_BOOK",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "sunrise-clinic",
                null,
                true
        );
        PublicProviderProfileDetailRecord hospitalDetail = detailRecord(
                ProviderType.HOSPITAL,
                "HS-0001",
                "city-care-hospital",
                "/discover/hospitals/city-care-hospital",
                "City Care Hospital",
                "City Care Hospital",
                "Hospital subtitle",
                "Hospital summary",
                "Hospital biography",
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(),
                List.of("Cardiology"),
                List.of(),
                List.of("Consultation"),
                List.of("Inpatient"),
                List.of(),
                List.of("In-person"),
                List.of(new PublicProviderLocationSnapshot("Primary", "Main Road", "Pune", "Maharashtra", "India", "411001", null, true, true, null, null)),
                List.of(),
                List.of(),
                null,
                null,
                null,
                "9876543210",
                "hospital@example.com",
                "https://example.com",
                "Pune",
                "Main Road",
                "Maharashtra",
                "India",
                "Cardiology",
                null,
                null,
                null,
                null,
                false,
                "CALL_TO_BOOK",
                false,
                OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                1,
                "city-care-hospital",
                null,
                true
        );

        when(publicProfileService.findBySlug("dr-asha-menon")).thenReturn(Optional.of(doctorDetail));
        when(associationService.findActiveAssociationsByPublicDoctorReference(doctorDetail.providerId())).thenReturn(List.of(mock(DiscoverPublicDoctorPracticeAssociationEntity.class)));
        when(associationService.listPublishedPracticeReferencesByDoctor(doctorDetail.providerId())).thenReturn(List.of(clinicDetail.providerId(), hospitalDetail.providerId()));
        when(publicProfileService.findByProviderId(clinicDetail.providerId())).thenReturn(Optional.of(clinicDetail));
        when(publicProfileService.findByProviderId(hospitalDetail.providerId())).thenReturn(Optional.of(hospitalDetail));

        var detail = facade.doctorDetail("dr-asha-menon");

        assertThat(detail.practices()).hasSize(2);
        assertThat(detail.clinics()).singleElement().satisfies(clinic -> assertThat(clinic.clinicSlug()).isEqualTo("sunrise-clinic"));
        assertThat(detail.practices()).extracting("practiceType").containsExactly("CLINIC", "HOSPITAL");
    }

    @Test
    void hospitalDetailUsesPublicMediaRoutesForPublishedAssets() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = facade(publicProfileService);

        when(publicProfileService.findBySlug("city-care-hospital")).thenReturn(Optional.of(
                detailRecord(
                        ProviderType.HOSPITAL,
                        "JHS-0001",
                        "city-care-hospital",
                        "/discover/hospitals/city-care-hospital",
                        "City Care Hospital",
                        "City Care Hospital",
                        "Hospital summary",
                        "Hospital description",
                        "Hospital biography",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        List.of(),
                        List.of("General Medicine"),
                        List.of(),
                        List.of("Consultation"),
                        List.of("General Medicine", "Cardiology"),
                        List.of("Wheelchair Access"),
                        List.of("In-person"),
                        List.of(new PublicProviderLocationSnapshot("Primary", "Main Road", "Pune", "Maharashtra", "India", "411001", "24x7", true, true, null, null)),
                        List.of(
                                new PublicProviderGalleryImageSnapshot(UUID.randomUUID(), "gallery-one.png"),
                                new PublicProviderGalleryImageSnapshot(UUID.randomUUID(), "gallery-two.png")
                        ),
                        List.of("http://minio/internal/gallery-one.png", "http://minio/internal/gallery-two.png"),
                        "http://minio/internal/hospital-image.png",
                        "http://minio/internal/hospital-cover.png",
                        "http://minio/internal/hospital-logo.png",
                        "9876543210",
                        "hospital@example.com",
                        "https://example.com",
                        "Pune",
                        "Main Road",
                        "Maharashtra",
                        "India",
                        "General Medicine",
                        "Private",
                        "Multispeciality Hospital",
                        "Dr Example",
                        250,
                        true,
                        "CALL_TO_BOOK",
                        false,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                        1,
                        "city-care-hospital",
                        null,
                        true
                )
        ));

        var detail = facade.hospitalDetail("city-care-hospital");

        assertThat(detail.logoUrl()).isEqualTo("/api/public/hospitals/city-care-hospital/logo");
        assertThat(detail.coverUrl()).isEqualTo("/api/public/hospitals/city-care-hospital/cover");
        assertThat(detail.bookingMode()).isEqualTo("CALL_TO_BOOK");
        assertThat(detail.galleryImageUrls()).containsExactly(
                "/api/public/hospitals/city-care-hospital/gallery/0",
                "/api/public/hospitals/city-care-hospital/gallery/1"
        );
    }

    @Test
    void doctorDetailReturnsCanonicalPublishedPath() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = facade(publicProfileService);

        when(publicProfileService.findBySlug("dr-asha-menon")).thenReturn(Optional.of(
                detailRecord(
                        ProviderType.INDIVIDUAL_DOCTOR,
                        "JDN-0001",
                        "dr-asha-menon",
                        "/discover/doctors/dr-asha-menon",
                        "Dr. Asha Menon",
                        "Dr. Asha Menon",
                        "Experienced public doctor",
                        "Doctor summary",
                        "Professional biography",
                        "MBBS",
                        "MCI",
                        8,
                        new BigDecimal("500"),
                        15,
                        true,
                        List.of("English", "Hindi"),
                        List.of("Dermatology"),
                        List.of("Skin Care"),
                        List.of("Consultations"),
                        List.of(),
                        List.of(),
                        List.of("In-person"),
                        List.of(new PublicProviderLocationSnapshot("Primary", "Baner Road", "Pune", "Maharashtra", "India", "411045", "Mon-Sat 9 AM-5 PM", true, true, new BigDecimal("18.520400"), new BigDecimal("73.856700"))),
                        List.of(new PublicProviderGalleryImageSnapshot(UUID.randomUUID(), "gallery.png")),
                        List.of("https://example.com/doctor-gallery.jpg"),
                        "https://example.com/doctor.jpg",
                        "https://example.com/doctor-cover.jpg",
                        null,
                        "1234567890",
                        "doctor@example.com",
                        "https://example.com",
                        "Pune",
                        "Baner",
                        "Maharashtra",
                        "India",
                        "General Medicine",
                        null,
                        null,
                        null,
                        null,
                        false,
                        "ONLINE_BOOKING",
                        true,
                        OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                        1,
                        "dr-asha-menon",
                        null,
                        true
                )
        ));

        var detail = facade.doctorDetail("dr-asha-menon");

        assertThat(detail.publicPath()).isEqualTo("/discover/doctors/dr-asha-menon");
        assertThat(detail.canonicalSlug()).isEqualTo("dr-asha-menon");
        assertThat(detail.photoUrl()).isEqualTo("/api/public/doctors/dr-asha-menon/photo");
        assertThat(detail.coverUrl()).isEqualTo("/api/public/doctors/dr-asha-menon/cover");
        assertThat(detail.galleryImageUrls()).containsExactly("/api/public/doctors/dr-asha-menon/gallery/0");
        assertThat(detail.reviewsComingSoon()).isTrue();
        assertThat(detail.availableToday()).isFalse();
        assertThat(detail.locations()).singleElement().satisfies(location -> {
            assertThat(location.latitude()).isEqualByComparingTo(new BigDecimal("18.520400"));
            assertThat(location.longitude()).isEqualByComparingTo(new BigDecimal("73.856700"));
        });
    }

    private static PublicProviderProfileSummaryRecord summaryRecord(
            ProviderType type,
            String canonicalSlug,
            String publicPath,
            String displayName,
            String summary,
            String primarySpeciality,
            String city,
            String area,
            String imageUrl,
            String coverUrl,
            int doctorCount,
            int serviceCount,
            int departmentCount,
            int galleryCount,
            String contactPhone,
            String bookingMode,
            boolean emergencyAvailable,
            List<String> tags
    ) {
        return new PublicProviderProfileSummaryRecord(
                UUID.randomUUID(),
                type,
                canonicalSlug,
                publicPath,
                displayName,
                displayName + " subtitle",
                summary,
                primarySpeciality,
                city,
                area,
                imageUrl,
                coverUrl,
                doctorCount,
                serviceCount,
                departmentCount,
                galleryCount,
                contactPhone,
                bookingMode,
                emergencyAvailable,
                tags,
                null
        );
    }

    private static PublicProviderProfileDetailRecord detailRecord(
            ProviderType type,
            String referenceNumber,
            String canonicalSlug,
            String publicPath,
            String displayName,
            String legalName,
            String subtitle,
            String summary,
            String biography,
            String qualification,
            String medicalCouncil,
            Integer yearsOfExperience,
            BigDecimal consultationFee,
            Integer appointmentDurationMinutes,
            boolean onlineConsultation,
            List<String> languages,
            List<String> specialities,
            List<String> subSpecialities,
            List<String> services,
            List<String> departments,
            List<String> facilities,
            List<String> consultationModes,
            List<PublicProviderLocationSnapshot> locations,
            List<?> gallery,
            List<String> galleryImageUrls,
            String imageUrl,
            String coverUrl,
            String logoUrl,
            String contactPhone,
            String contactEmail,
            String website,
            String city,
            String area,
            String state,
            String country,
            String primarySpeciality,
            String ownership,
            String hospitalType,
            String medicalDirector,
            Integer beds,
            boolean emergencyAvailable,
            String bookingMode,
            boolean reviewsComingSoon,
            OffsetDateTime publishedAt,
            int publishedVersionNumber,
            String slug,
            String previousSlug,
            boolean canonical
    ) {
        @SuppressWarnings("unchecked")
        List<com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot> typedGallery =
                (List<com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderGalleryImageSnapshot>) gallery;
        return new PublicProviderProfileDetailRecord(
                UUID.randomUUID(),
                type,
                referenceNumber,
                canonicalSlug,
                publicPath,
                displayName,
                legalName,
                subtitle,
                summary,
                biography,
                qualification,
                medicalCouncil,
                yearsOfExperience,
                consultationFee,
                appointmentDurationMinutes,
                onlineConsultation,
                languages,
                specialities,
                subSpecialities,
                services,
                departments,
                facilities,
                consultationModes,
                locations,
                typedGallery,
                galleryImageUrls,
                imageUrl,
                coverUrl,
                logoUrl,
                contactPhone,
                contactEmail,
                website,
                city,
                area,
                state,
                country,
                primarySpeciality,
                ownership,
                hospitalType,
                medicalDirector,
                beds,
                emergencyAvailable,
                bookingMode,
                reviewsComingSoon,
                publishedAt,
                publishedVersionNumber,
                slug,
                previousSlug,
                canonical,
                List.of(new com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderTimingSnapshot(
                        "MONDAY", "09:00", "17:00", 0
                )),
                "Asia/Kolkata"
        );
    }

    private static PublicCatalogFacade facade(ProviderPublicProfileService publicProfileService) {
        PublicDoctorPracticeAssociationService associationService = mock(PublicDoctorPracticeAssociationService.class);
        when(associationService.listPublishedDoctorReferencesByPractice(any())).thenReturn(List.of());
        when(associationService.listPublishedPracticeReferencesByDoctor(any())).thenReturn(List.of());
        when(associationService.findActiveAssociationsByPublicDoctorReference(any())).thenReturn(List.of());
        when(associationService.findActiveAssociationsByPublicPracticeReference(any())).thenReturn(List.of());
        return new PublicCatalogFacade(publicProfileService, mock(ProviderLinkingService.class), moderationService(), associationService, mock(PublicHospitalDoctorAssociationService.class));
    }
}
