package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicHospitalSummaryResponse(
        String hospitalSlug,
        String publicPath,
        String hospitalDisplayName,
        String logoUrl,
        String coverUrl,
        String area,
        String city,
        int doctorsCount,
        int serviceCount,
        int departmentCount,
        int galleryCount,
        boolean emergencyAvailable,
        List<String> departments,
        String subtitle,
        String summary
) {
}
