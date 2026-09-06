package com.deepthoughtnet.clinic.api.patientportal.dto;

public record PatientPortalClinicResponse(
        String clinicId,
        String tenantId,
        String clinicName,
        String displayName,
        String phone,
        String email,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String postalCode,
        boolean active,
        boolean bookable,
        String slug
) {
}
