package com.deepthoughtnet.clinic.api.patient.dto;

import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PatientResponse(
        String id,
        String tenantId,
        String patientNumber,
        String firstName,
        String lastName,
        PatientGender gender,
        LocalDate dateOfBirth,
        Integer ageYears,
        String mobile,
        String email,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String country,
        String postalCode,
        String emergencyContactName,
        String emergencyContactMobile,
        String bloodGroup,
        String allergies,
        String existingConditions,
        String notes,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
