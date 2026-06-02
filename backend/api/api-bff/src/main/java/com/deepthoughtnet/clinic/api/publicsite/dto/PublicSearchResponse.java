package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicSearchResponse(
        List<PublicClinicSummaryResponse> clinics,
        List<PublicDoctorSummaryResponse> doctors,
        List<String> specialities
) {
}
