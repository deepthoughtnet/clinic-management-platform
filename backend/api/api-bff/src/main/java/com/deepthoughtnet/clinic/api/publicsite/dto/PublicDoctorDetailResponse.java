package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicDoctorDetailResponse(
        String publicDoctorId,
        String doctorSlug,
        String doctorDisplayName,
        String photoUrl,
        String qualification,
        Integer yearsOfExperience,
        List<String> specialities,
        List<String> languages,
        List<PublicClinicMiniResponse> clinics,
        List<String> availableDays,
        List<String> nextAvailableSlots,
        boolean availableToday
) {
}
