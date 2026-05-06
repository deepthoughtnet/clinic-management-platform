package com.deepthoughtnet.clinic.patient.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientRecord(
        UUID id,
        UUID tenantId,
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
    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    }
}
