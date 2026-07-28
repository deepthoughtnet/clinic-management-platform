package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicDoctorSummaryResponse(
        String publicDoctorId,
        String doctorSlug,
        String publicPath,
        String doctorDisplayName,
        String photoUrl,
        String speciality,
        Integer yearsOfExperience,
        List<String> languages,
        String area,
        String city,
        String subtitle,
        String summary,
        String clinicDisplayName,
        String clinicSlug,
        boolean availableToday,
        String nextAvailableSlotSummary
) {
}
