package com.deepthoughtnet.clinic.api.publicsite.dto;

public record PublicClinicMiniResponse(
        String clinicSlug,
        String clinicDisplayName,
        String area,
        String city,
        String bookingReference
) {
    public PublicClinicMiniResponse(String clinicSlug, String clinicDisplayName, String area, String city) {
        this(clinicSlug, clinicDisplayName, area, city, null);
    }
}
