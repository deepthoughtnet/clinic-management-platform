package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicDoctorSummaryResponse(
        String publicDoctorId,
        String doctorSlug,
        String publicPath,
        String doctorDisplayName,
        String photoUrl,
        String contactPhone,
        String speciality,
        Integer yearsOfExperience,
        java.math.BigDecimal consultationFee,
        List<String> languages,
        String area,
        String city,
        String bookingMode,
        String subtitle,
        String summary,
        String clinicDisplayName,
        String clinicSlug,
        boolean availableToday,
        String nextAvailableSlotSummary,
        java.math.BigDecimal distanceKm
) {
}
