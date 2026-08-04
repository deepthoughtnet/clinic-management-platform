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
        java.math.BigDecimal distanceKm,
        String bookingReference
) {
    public PublicDoctorSummaryResponse(
            String publicDoctorId,
            String doctorSlug,
            String publicPath,
            String doctorDisplayName,
            String photoUrl,
            String contactPhone,
            String speciality,
            Integer yearsOfExperience,
            java.math.BigDecimal consultationFee,
            java.util.List<String> languages,
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
        this(
                publicDoctorId,
                doctorSlug,
                publicPath,
                doctorDisplayName,
                photoUrl,
                contactPhone,
                speciality,
                yearsOfExperience,
                consultationFee,
                languages,
                area,
                city,
                bookingMode,
                subtitle,
                summary,
                clinicDisplayName,
                clinicSlug,
                availableToday,
                nextAvailableSlotSummary,
                distanceKm,
                null
        );
    }
}
