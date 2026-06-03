package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicSearchResponse(
        PublicPageResponse<PublicDoctorSummaryResponse> doctors,
        PublicPageResponse<PublicClinicSummaryResponse> clinics,
        List<PublicSpecialitySummaryResponse> specialities
) {
}
