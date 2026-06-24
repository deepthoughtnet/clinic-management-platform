package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicDoctorSummaryResponse(
        String publicDoctorId,
        String doctorSlug,
        String doctorDisplayName,
        String photoUrl,
        String speciality,
        Integer yearsOfExperience,
        List<String> languages,
        String clinicDisplayName,
        String clinicSlug,
        String area,
        String city,
        boolean availableToday,
        String nextAvailableSlotSummary
) {
}
