package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicClinicDetailResponse(
        String clinicSlug,
        String canonicalSlug,
        String publicPath,
        String clinicDisplayName,
        String logoUrl,
        String coverUrl,
        String bookingMode,
        String address,
        String area,
        String city,
        String summary,
        String description,
        List<String> specialities,
        List<String> services,
        List<String> departments,
        List<String> facilities,
        List<String> consultationModes,
        List<PublicProviderLocationResponse> locations,
        List<String> galleryImageUrls,
        List<PublicDoctorSummaryResponse> doctors,
        String contactPhone,
        String contactEmail,
        String website,
        List<String> timings,
        boolean availableToday,
        boolean reviewsComingSoon,
        String subtitle
) {
}
