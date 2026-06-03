package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicClinicDetailResponse(
        String clinicSlug,
        String clinicDisplayName,
        String logoUrl,
        String address,
        String area,
        String city,
        List<String> timings,
        List<PublicDoctorSummaryResponse> doctors,
        List<String> specialities,
        boolean availableToday
) {
}
