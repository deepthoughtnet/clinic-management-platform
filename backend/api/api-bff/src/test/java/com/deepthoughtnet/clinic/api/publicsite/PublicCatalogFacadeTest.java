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
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicSpecialitySummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderSearchCriteria;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PublicCatalogFacadeTest {

    @Test
    void mapsPublishedProfilesIntoPublicListsAndSearchResults() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService);

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
        });
        assertThat(search.hospitals().items()).singleElement().satisfies(item -> assertThat(item.hospitalDisplayName()).isEqualTo("City Care Hospital"));
        assertThat(facade.listSpecialities("skin", "pune", "demo")).extracting("speciality").containsExactly("Dermatology");
    }

    @Test
    void clinicDetailUsesPublicMediaRoutesForPublishedAssets() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService);

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
    }

    @Test
    void hospitalDetailUsesPublicMediaRoutesForPublishedAssets() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService);

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
        assertThat(detail.galleryImageUrls()).containsExactly(
                "/api/public/hospitals/city-care-hospital/gallery/0",
                "/api/public/hospitals/city-care-hospital/gallery/1"
        );
    }

    @Test
    void doctorDetailReturnsCanonicalPublishedPath() {
        ProviderPublicProfileService publicProfileService = mock(ProviderPublicProfileService.class);
        PublicCatalogFacade facade = new PublicCatalogFacade(publicProfileService);

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
                canonical
        );
    }
}
