package com.deepthoughtnet.clinic.api.publicsite.dto;

public record PublicClinicMiniResponse(
        String clinicSlug,
        String clinicDisplayName,
        String area,
        String city
) {
}
