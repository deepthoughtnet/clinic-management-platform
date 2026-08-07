package com.deepthoughtnet.clinic.discover.publicprofile;

import com.deepthoughtnet.clinic.discover.onboarding.ProviderOnboardingEnums.ProviderType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PublicProviderProfileModels {
    private PublicProviderProfileModels() {
    }

    public record PublicProviderLocationSnapshot(
            String label,
            String address,
            String city,
            String state,
            String country,
            String pinCode,
            String workingHours,
            boolean parkingAvailable,
            boolean accessibilityAvailable,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }

    public record PublicProviderGalleryImageSnapshot(
            UUID documentId,
            String caption
    ) {
    }

    public record PublicProviderPublishedMediaSnapshot(
            UUID mediaReference,
            String mediaType,
            String storageKey,
            String contentType,
            String originalFilename,
            String altText,
            int displayOrder
    ) {
    }

    public record PublicProviderTimingSnapshot(
            String day,
            String open,
            String close,
            int displayOrder
    ) {
    }

    public record PublicProviderProfileSnapshot(
            UUID providerId,
            ProviderType providerType,
            String sourceSystem,
            String referenceNumber,
            String displayName,
            String legalName,
            String canonicalSlug,
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
            List<PublicProviderGalleryImageSnapshot> gallery,
            List<String> galleryImageUrls,
            UUID logoDocumentId,
            UUID coverImageDocumentId,
            UUID doctorPhotoDocumentId,
            String contactPhone,
            String contactEmail,
            String website,
            String city,
            String area,
            String state,
            String country,
            String primarySpeciality,
            String tagline,
            String ownership,
            String hospitalType,
            String medicalDirector,
            Integer beds,
            boolean emergencyAvailable,
            int doctorCount,
            int serviceCount,
            int departmentCount,
            int galleryCount,
            String bookingMode,
            boolean reviewsComingSoon,
            OffsetDateTime publishedAt,
            int publishedVersionNumber,
            String publicPath,
            List<PublicProviderPublishedMediaSnapshot> publishedMedia,
            List<PublicProviderTimingSnapshot> weeklyTimings,
            String timingTimezone
    ) {
    }

    public record PublicProviderProfileSummaryRecord(
            UUID providerId,
            ProviderType providerType,
            String canonicalSlug,
            String publicPath,
            String displayName,
            String subtitle,
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
            List<String> tags,
            BigDecimal distanceKm
    ) {
    }

    public record PublicProviderProfileDetailRecord(
            UUID providerId,
            ProviderType providerType,
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
            List<PublicProviderGalleryImageSnapshot> gallery,
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
            boolean canonical,
            List<PublicProviderTimingSnapshot> weeklyTimings,
            String timingTimezone
    ) {
    }

    public record PublicProviderPublicationRecord(
            UUID providerId,
            ProviderType providerType,
            String canonicalSlug,
            int publishedVersionNumber,
            OffsetDateTime publishedAt,
            String publicPath
    ) {
    }

    public record PublicSpecialitySummaryRecord(
            String speciality,
            String specialitySlug,
            int doctorsCount,
            int clinicsCount,
            int hospitalsCount
    ) {
    }

    public record PublicProviderSearchCriteria(
            ProviderType providerType,
            String query,
            String city,
            String area,
            String speciality,
            String service,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer radiusKm
    ) {
    }

    public record PublicProfileMediaContent(
            String contentType,
            String originalFilename,
            byte[] bytes
    ) {
    }

    public static PublicProviderProfileSnapshot healthcareClinicSnapshot(
            UUID providerId,
            String sourceSystem,
            String referenceNumber,
            String canonicalSlug,
            String displayName,
            String legalName,
            String summary,
            List<String> specialities,
            List<PublicProviderLocationSnapshot> locations,
            UUID logoDocumentId,
            String contactPhone,
            String contactEmail,
            String city,
            String area,
            String state,
            String country,
            String bookingMode,
            OffsetDateTime publishedAt,
            int publishedVersionNumber,
            String publicPath,
            int doctorCount
    ) {
        String primarySpeciality = specialities == null || specialities.isEmpty() ? null : specialities.getFirst();
        return new PublicProviderProfileSnapshot(
                providerId,
                ProviderType.CLINIC,
                sourceSystem,
                referenceNumber,
                displayName,
                legalName,
                canonicalSlug,
                summary,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                List.of(),
                specialities == null ? List.of() : specialities,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                locations == null ? List.of() : locations,
                List.of(),
                List.of(),
                logoDocumentId,
                null,
                null,
                contactPhone,
                contactEmail,
                null,
                city,
                area,
                state,
                country,
                primarySpeciality,
                null,
                null,
                null,
                null,
                null,
                false,
                doctorCount,
                0,
                0,
                0,
                bookingMode,
                false,
                publishedAt,
                publishedVersionNumber,
                publicPath,
                List.of(),
                List.of(),
                null
        );
    }

    public static PublicProviderProfileSnapshot healthcareDoctorSnapshot(
            UUID providerId,
            String sourceSystem,
            String referenceNumber,
            String canonicalSlug,
            String displayName,
            String legalName,
            String summary,
            String biography,
            String qualification,
            String medicalCouncil,
            Integer yearsOfExperience,
            BigDecimal consultationFee,
            Integer appointmentDurationMinutes,
            boolean onlineConsultation,
            List<String> specialities,
            List<PublicProviderLocationSnapshot> locations,
            UUID doctorPhotoDocumentId,
            String contactPhone,
            String contactEmail,
            String city,
            String area,
            String state,
            String country,
            String bookingMode,
            OffsetDateTime publishedAt,
            int publishedVersionNumber,
            String publicPath
    ) {
        String primarySpeciality = specialities == null || specialities.isEmpty() ? null : specialities.getFirst();
        return new PublicProviderProfileSnapshot(
                providerId,
                ProviderType.INDIVIDUAL_DOCTOR,
                sourceSystem,
                referenceNumber,
                displayName,
                legalName,
                canonicalSlug,
                summary,
                biography,
                qualification,
                medicalCouncil,
                yearsOfExperience,
                consultationFee,
                appointmentDurationMinutes,
                onlineConsultation,
                List.of(),
                specialities == null ? List.of() : specialities,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                locations == null ? List.of() : locations,
                List.of(),
                List.of(),
                null,
                null,
                doctorPhotoDocumentId,
                contactPhone,
                contactEmail,
                null,
                city,
                area,
                state,
                country,
                primarySpeciality,
                null,
                null,
                null,
                null,
                null,
                false,
                1,
                0,
                0,
                0,
                bookingMode,
                false,
                publishedAt,
                publishedVersionNumber,
                publicPath,
                List.of(),
                List.of(),
                null
        );
    }
}
