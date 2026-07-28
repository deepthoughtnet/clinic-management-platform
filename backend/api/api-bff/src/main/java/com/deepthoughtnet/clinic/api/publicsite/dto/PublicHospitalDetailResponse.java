package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicHospitalDetailResponse(
        String hospitalSlug,
        String canonicalSlug,
        String publicPath,
        String hospitalDisplayName,
        String logoUrl,
        String coverUrl,
        String address,
        String area,
        String city,
        String summary,
        String description,
        List<String> departments,
        List<String> facilities,
        List<String> services,
        List<String> consultationModes,
        List<PublicProviderLocationResponse> locations,
        List<String> galleryImageUrls,
        List<PublicDoctorSummaryResponse> doctors,
        String contactPhone,
        String contactEmail,
        String website,
        boolean emergencyAvailable,
        boolean reviewsComingSoon,
        String subtitle
) {
}
