package com.deepthoughtnet.clinic.api.publicsite.dto;

import java.util.List;

public record PublicClinicSummaryResponse(
        String clinicDisplayName,
        String city,
        String locationLabel,
        List<String> specialities
) {
}
