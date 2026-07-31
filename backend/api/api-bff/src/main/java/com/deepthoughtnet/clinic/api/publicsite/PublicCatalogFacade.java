package com.deepthoughtnet.clinic.api.publicsite;

import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicMiniResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicClinicSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicDoctorSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicHospitalDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicHospitalSummaryResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicPageResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicProviderLocationResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSearchResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialityDetailResponse;
import com.deepthoughtnet.clinic.api.publicsite.dto.PublicSpecialitySummaryResponse;
import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService.DoctorPublicMediaAsset;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProfileMediaContent;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderLocationSnapshot;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileDetailRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderProfileSummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicSpecialitySummaryRecord;
import com.deepthoughtnet.clinic.discover.publicprofile.PublicProviderProfileModels.PublicProviderSearchCriteria;
import com.deepthoughtnet.clinic.discover.publicprofile.ProviderPublicProfileService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicCatalogFacade {
    private final ProviderPublicProfileService publicProfileService;

    public PublicCatalogFacade(ProviderPublicProfileService publicProfileService) {
        this.publicProfileService = publicProfileService;
    }

    public PublicPageResponse<PublicClinicSummaryResponse> listClinics(
            String q,
            String city,
            String area,
            String speciality,
            String tenantCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusKm,
            int page,
            int size
    ) {
        return pageOf(mapClinicSummaries(
                publicProfileService.listProfiles(new PublicProviderSearchCriteria(
                        ProviderType.CLINIC,
                        mergeQuery(q, null),
                        city,
                        area,
                        speciality,
                        null,
                        latitude,
                        longitude,
                        radiusKm
                ), page, size)
        ));
    }

    public PublicClinicDetailResponse clinicDetail(String clinicSlug) {
        PublicProviderProfileDetailRecord detail = findDetail(clinicSlug, ProviderType.CLINIC);
        return toClinicDetail(detail);
    }

    public PublicProfileMediaContent clinicLogo(String clinicSlug) {
        return publicProfileService.loadPublishedProviderLogo(clinicSlug, ProviderType.CLINIC)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile image not found"));
    }

    public PublicPageResponse<PublicDoctorSummaryResponse> listDoctors(
            String q,
            String city,
            String area,
            String speciality,
            String clinic,
            String tenantCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusKm,
            int page,
            int size
    ) {
        return pageOf(mapDoctorSummaries(
                publicProfileService.listProfiles(new PublicProviderSearchCriteria(
                        ProviderType.INDIVIDUAL_DOCTOR,
                        mergeQuery(q, clinic),
                        city,
                        area,
                        speciality,
                        clinic,
                        latitude,
                        longitude,
                        radiusKm
                ), page, size)
        ));
    }

    public PublicDoctorDetailResponse doctorDetail(String doctorSlug) {
        PublicProviderProfileDetailRecord detail = findDetail(doctorSlug, ProviderType.INDIVIDUAL_DOCTOR);
        return toDoctorDetail(detail, doctorSlug);
    }

    public PublicProfileMediaContent doctorPhoto(String doctorSlug) {
        return publicProfileService.loadPublishedDoctorMedia(doctorSlug, DoctorPublicMediaAsset.PHOTO, null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile image not found"));
    }

    public PublicProfileMediaContent doctorCover(String doctorSlug) {
        return publicProfileService.loadPublishedDoctorMedia(doctorSlug, DoctorPublicMediaAsset.COVER, null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile image not found"));
    }

    public PublicProfileMediaContent doctorGalleryImage(String doctorSlug, int index) {
        return publicProfileService.loadPublishedDoctorMedia(doctorSlug, DoctorPublicMediaAsset.GALLERY, index)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile image not found"));
    }

    public PublicPageResponse<PublicHospitalSummaryResponse> listHospitals(
            String q,
            String city,
            String area,
            String speciality,
            String tenantCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusKm,
            int page,
            int size
    ) {
        return pageOf(mapHospitalSummaries(
                publicProfileService.listProfiles(new PublicProviderSearchCriteria(
                        ProviderType.HOSPITAL,
                        q,
                        city,
                        area,
                        speciality,
                        null,
                        latitude,
                        longitude,
                        radiusKm
                ), page, size)
        ));
    }

    public PublicHospitalDetailResponse hospitalDetail(String hospitalSlug) {
        PublicProviderProfileDetailRecord detail = findDetail(hospitalSlug, ProviderType.HOSPITAL);
        return toHospitalDetail(detail);
    }

    public PublicProfileMediaContent hospitalLogo(String hospitalSlug) {
        return publicProfileService.loadPublishedProviderLogo(hospitalSlug, ProviderType.HOSPITAL)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile image not found"));
    }

    public List<PublicSpecialitySummaryResponse> listSpecialities(String q, String city, String tenantCode) {
        return publicProfileService.listSpecialities(q, city).stream()
                .map(item -> new PublicSpecialitySummaryResponse(
                        item.speciality(),
                        item.specialitySlug(),
                        item.doctorsCount(),
                        item.clinicsCount(),
                        item.hospitalsCount()
                ))
                .toList();
    }

    public PublicSpecialityDetailResponse specialityDetail(
            String specialitySlug,
            String q,
            String city,
            String area,
            String clinic,
            String tenantCode,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusKm,
            int page,
            int size
    ) {
        String speciality = resolveSpecialityLabel(specialitySlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speciality not found"));
        PublicPageResponse<PublicDoctorSummaryResponse> doctors = pageOf(mapDoctorSummaries(
                publicProfileService.listProfiles(new PublicProviderSearchCriteria(
                        ProviderType.INDIVIDUAL_DOCTOR,
                        mergeQuery(q, clinic),
                        city,
                        area,
                        speciality,
                        clinic,
                        latitude,
                        longitude,
                        radiusKm
                ), page, size)
        ));
        return new PublicSpecialityDetailResponse(speciality, slugify(speciality), doctors);
    }

    public PublicSearchResponse search(String q, String city, String area, String tenantCode, BigDecimal latitude, BigDecimal longitude, Integer radiusKm, int page, int size) {
        return new PublicSearchResponse(
                listDoctors(q, city, area, null, null, tenantCode, latitude, longitude, radiusKm, page, size),
                listClinics(q, city, area, null, tenantCode, latitude, longitude, radiusKm, page, size),
                listHospitals(q, city, area, null, tenantCode, latitude, longitude, radiusKm, page, size),
                listSpecialities(q, city, tenantCode)
        );
    }

    private PublicProviderProfileDetailRecord findDetail(String slug, ProviderType providerType) {
        PublicProviderProfileDetailRecord detail = publicProfileService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile not found"));
        if (detail.providerType() != providerType) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Public profile not found");
        }
        return detail;
    }

    private PublicDoctorDetailResponse toDoctorDetail(PublicProviderProfileDetailRecord detail, String doctorSlug) {
        List<String> gallery = IntStream.range(0, detail.gallery().size())
                .filter(index -> detail.gallery().get(index).documentId() != null)
                .mapToObj(index -> publicDoctorGalleryImagePath(doctorSlug, index))
                .toList();
        List<PublicClinicMiniResponse> clinics = detail.locations().isEmpty()
                ? List.of()
                : List.of(new PublicClinicMiniResponse(
                        slugify(detail.locations().get(0).label() == null ? detail.canonicalSlug() : detail.locations().get(0).label()),
                        firstNonBlank(detail.locations().get(0).label(), detail.displayName()),
                        detail.locations().get(0).city(),
                        detail.city()
                ));
        return new PublicDoctorDetailResponse(
                detail.providerId().toString(),
                detail.slug(),
                detail.canonicalSlug(),
                detail.publicPath(),
                detail.displayName(),
                publicDoctorPhotoPath(doctorSlug),
                detail.qualification(),
                detail.medicalCouncil(),
                detail.yearsOfExperience(),
                detail.summary(),
                detail.biography(),
                detail.specialities(),
                detail.subSpecialities(),
                detail.languages(),
                detail.consultationModes(),
                detail.services(),
                detail.locations().stream().map(this::toLocationResponse).toList(),
                gallery,
                publicDoctorCoverPath(doctorSlug),
                detail.logoUrl(),
                detail.contactPhone(),
                detail.contactEmail(),
                detail.website(),
                detail.area(),
                detail.city(),
                detail.state(),
                detail.country(),
                detail.primarySpeciality(),
                detail.reviewsComingSoon(),
                detail.subtitle(),
                detail.summary(),
                clinics,
                List.of(),
                List.of(),
                false
        );
    }

    private String publicDoctorPhotoPath(String doctorSlug) {
        return "/api/public/doctors/" + doctorSlug + "/photo";
    }

    private String publicDoctorCoverPath(String doctorSlug) {
        return "/api/public/doctors/" + doctorSlug + "/cover";
    }

    private String publicDoctorGalleryImagePath(String doctorSlug, int index) {
        return "/api/public/doctors/" + doctorSlug + "/gallery/" + index;
    }

    private String publicClinicLogoPath(String clinicSlug) {
        return "/api/public/clinics/" + clinicSlug + "/logo";
    }

    private String publicHospitalLogoPath(String hospitalSlug) {
        return "/api/public/hospitals/" + hospitalSlug + "/logo";
    }

    private PublicClinicDetailResponse toClinicDetail(PublicProviderProfileDetailRecord detail) {
        List<String> gallery = detail.gallery().stream()
                .map(image -> publicProfileService.resolveDocumentUrl(image.documentId()).orElse(null))
                .filter(url -> url != null && !url.isBlank())
                .toList();
        return new PublicClinicDetailResponse(
                detail.slug(),
                detail.canonicalSlug(),
                detail.publicPath(),
                detail.displayName(),
                detail.logoUrl(),
                detail.coverUrl(),
                firstNonBlank(detail.locations().stream().findFirst().map(PublicProviderLocationSnapshot::address).orElse(null), detail.summary()),
                detail.area(),
                detail.city(),
                detail.summary(),
                detail.biography(),
                detail.specialities(),
                detail.services(),
                detail.departments(),
                detail.facilities(),
                detail.consultationModes(),
                detail.locations().stream().map(this::toLocationResponse).toList(),
                gallery,
                List.of(),
                detail.contactPhone(),
                detail.contactEmail(),
                detail.website(),
                detail.locations().stream()
                        .map(PublicProviderLocationSnapshot::workingHours)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList(),
                false,
                detail.reviewsComingSoon(),
                detail.subtitle()
        );
    }

    private PublicHospitalDetailResponse toHospitalDetail(PublicProviderProfileDetailRecord detail) {
        List<String> gallery = detail.gallery().stream()
                .map(image -> publicProfileService.resolveDocumentUrl(image.documentId()).orElse(null))
                .filter(url -> url != null && !url.isBlank())
                .toList();
        return new PublicHospitalDetailResponse(
                detail.slug(),
                detail.canonicalSlug(),
                detail.publicPath(),
                detail.displayName(),
                detail.logoUrl(),
                detail.coverUrl(),
                firstNonBlank(detail.locations().stream().findFirst().map(PublicProviderLocationSnapshot::address).orElse(null), detail.summary()),
                detail.area(),
                detail.city(),
                detail.summary(),
                detail.biography(),
                detail.departments(),
                detail.facilities(),
                detail.services(),
                detail.consultationModes(),
                detail.locations().stream().map(this::toLocationResponse).toList(),
                gallery,
                List.of(),
                detail.contactPhone(),
                detail.contactEmail(),
                detail.website(),
                detail.emergencyAvailable(),
                detail.reviewsComingSoon(),
                detail.subtitle()
        );
    }

    private PublicProviderLocationResponse toLocationResponse(PublicProviderLocationSnapshot location) {
        return new PublicProviderLocationResponse(
                location.label(),
                location.address(),
                location.city(),
                location.state(),
                location.country(),
                location.pinCode(),
                location.workingHours(),
                location.parkingAvailable(),
                location.accessibilityAvailable(),
                location.latitude(),
                location.longitude()
        );
    }

    private PublicDoctorSummaryResponse toDoctorSummary(PublicProviderProfileSummaryRecord record) {
        PublicProviderProfileDetailRecord detail = publicProfileService.findBySlug(record.canonicalSlug()).orElse(null);
        PublicProviderLocationSnapshot location = detail == null || detail.locations().isEmpty() ? null : detail.locations().get(0);
        return new PublicDoctorSummaryResponse(
                record.providerId().toString(),
                record.canonicalSlug(),
                record.publicPath(),
                record.displayName(),
                record.imageUrl() == null || record.imageUrl().isBlank() ? null : publicDoctorPhotoPath(record.canonicalSlug()),
                record.primarySpeciality(),
                detail == null ? null : detail.yearsOfExperience(),
                detail == null ? null : detail.consultationFee(),
                detail == null ? List.of() : detail.languages(),
                location == null ? record.area() : location.label(),
                location == null ? record.city() : location.city(),
                record.subtitle(),
                record.summary(),
                firstNonBlank(location == null ? null : location.label(), record.subtitle(), record.primarySpeciality(), record.displayName()),
                slugify(firstNonBlank(location == null ? null : location.label(), record.area(), record.city(), record.displayName())),
                false,
                null,
                record.distanceKm()
        );
    }

    private PublicClinicSummaryResponse toClinicSummary(PublicProviderProfileSummaryRecord record) {
        return new PublicClinicSummaryResponse(
                record.canonicalSlug(),
                record.publicPath(),
                record.displayName(),
                record.imageUrl() == null || record.imageUrl().isBlank() ? null : publicClinicLogoPath(record.canonicalSlug()),
                record.coverUrl(),
                record.area(),
                record.area(),
                record.city(),
                record.doctorCount(),
                record.serviceCount(),
                record.departmentCount(),
                record.galleryCount(),
                record.emergencyAvailable(),
                record.tags(),
                record.subtitle(),
                record.summary(),
                false,
                record.distanceKm()
        );
    }

    private PublicHospitalSummaryResponse toHospitalSummary(PublicProviderProfileSummaryRecord record) {
        return new PublicHospitalSummaryResponse(
                record.canonicalSlug(),
                record.publicPath(),
                record.displayName(),
                record.imageUrl() == null || record.imageUrl().isBlank() ? null : publicHospitalLogoPath(record.canonicalSlug()),
                record.coverUrl(),
                record.area(),
                record.city(),
                record.doctorCount(),
                record.serviceCount(),
                record.departmentCount(),
                record.galleryCount(),
                record.emergencyAvailable(),
                record.tags(),
                record.subtitle(),
                record.summary(),
                record.distanceKm()
        );
    }

    private Page<PublicDoctorSummaryResponse> mapDoctorSummaries(Page<PublicProviderProfileSummaryRecord> page) {
        return page.map(this::toDoctorSummary);
    }

    private Page<PublicClinicSummaryResponse> mapClinicSummaries(Page<PublicProviderProfileSummaryRecord> page) {
        return page.map(this::toClinicSummary);
    }

    private Page<PublicHospitalSummaryResponse> mapHospitalSummaries(Page<PublicProviderProfileSummaryRecord> page) {
        return page.map(this::toHospitalSummary);
    }

    private <T> PublicPageResponse<T> pageOf(Page<T> page) {
        return new PublicPageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private String mergeQuery(String query, String extra) {
        return Stream.of(query, extra)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }

    private Optional<String> resolveSpecialityLabel(String specialitySlug) {
        String normalized = slugify(specialitySlug);
        return publicProfileService.listSpecialities(null, null).stream()
                .filter(item -> slugify(item.speciality()).equalsIgnoreCase(normalized))
                .map(PublicSpecialitySummaryRecord::speciality)
                .findFirst();
    }

    private String slugify(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
