package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicClinicSummaryResponse(
        String clinicSlug,
        String publicPath,
        String clinicDisplayName,
        String logoUrl,
        String coverUrl,
        String address,
        String area,
        String city,
        int doctorsCount,
        int serviceCount,
        int departmentCount,
        int galleryCount,
        boolean emergencyAvailable,
        List<String> specialities,
        String subtitle,
        String summary,
        boolean availableToday,
        java.math.BigDecimal distanceKm
) {
}
