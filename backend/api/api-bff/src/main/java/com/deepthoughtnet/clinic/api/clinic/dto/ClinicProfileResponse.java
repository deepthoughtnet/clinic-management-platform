package com.deepthoughtnet.clinic.api.clinic.dto;

import java.time.OffsetDateTime;

public record ClinicProfileResponse(
        String id,
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
        String registrationNumber,
        String gstNumber,
        String logoDocumentId,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
