package com.deepthoughtnet.clinic.api.publicsite.dto;

public record PublicSpecialitySummaryResponse(
        String speciality,
        String specialitySlug,
        long doctorsCount,
        long clinicsCount
) {
}
