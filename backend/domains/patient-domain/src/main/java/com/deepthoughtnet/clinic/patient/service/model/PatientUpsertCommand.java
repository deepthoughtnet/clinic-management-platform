package com.deepthoughtnet.clinic.patient.service.model;

import java.time.LocalDate;

public record PatientUpsertCommand(
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
        boolean active
) {
}
