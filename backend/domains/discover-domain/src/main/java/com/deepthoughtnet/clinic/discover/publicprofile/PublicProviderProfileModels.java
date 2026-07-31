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

    public record PublicProviderProfileSnapshot(
            UUID providerId,
            ProviderType providerType,
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
            boolean reviewsComingSoon,
            OffsetDateTime publishedAt,
            int publishedVersionNumber,
            String publicPath
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
            boolean reviewsComingSoon,
            OffsetDateTime publishedAt,
            int publishedVersionNumber,
            String slug,
            String previousSlug,
            boolean canonical
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
}
