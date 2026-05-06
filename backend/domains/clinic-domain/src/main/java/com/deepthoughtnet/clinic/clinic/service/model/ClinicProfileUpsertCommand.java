package com.deepthoughtnet.clinic.clinic.service.model;

import java.util.UUID;

public record ClinicProfileUpsertCommand(
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
        String registrationNumber,
        String gstNumber,
        UUID logoDocumentId,
        boolean active
) {
}
