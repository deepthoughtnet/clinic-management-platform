package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicDoctorDetailResponse(
        String publicDoctorId,
        String doctorSlug,
        String canonicalSlug,
        String publicPath,
        String doctorDisplayName,
        String photoUrl,
        String qualification,
        String medicalCouncil,
        Integer yearsOfExperience,
        String summary,
        String biography,
        List<String> specialities,
        List<String> subSpecialities,
        List<String> languages,
        List<String> consultationModes,
        List<String> services,
        List<PublicProviderLocationResponse> locations,
        List<String> galleryImageUrls,
        String coverUrl,
        String logoUrl,
        String contactPhone,
        String contactEmail,
        String website,
        String area,
        String city,
        String state,
        String country,
        String primarySpeciality,
        boolean reviewsComingSoon,
        String subtitle,
        String bookingSummary,
        List<PublicClinicMiniResponse> clinics,
        List<String> availableDays,
        List<String> nextAvailableSlots,
        boolean availableToday
) {
}
