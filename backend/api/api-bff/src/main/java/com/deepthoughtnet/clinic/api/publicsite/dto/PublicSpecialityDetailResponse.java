package com.deepthoughtnet.clinic.api.publicsite.dto;

public record PublicSpecialityDetailResponse(
        String speciality,
        String specialitySlug,
        PublicPageResponse<PublicDoctorSummaryResponse> doctors
) {
}
