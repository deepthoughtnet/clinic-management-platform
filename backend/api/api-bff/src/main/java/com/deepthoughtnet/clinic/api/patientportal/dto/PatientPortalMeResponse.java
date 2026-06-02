package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;

public record PatientPortalMeResponse(
        String patientId,
        String patientNumber,
        String firstName,
        String lastName,
        String fullName,
        String gender,
        LocalDate dateOfBirth,
        Integer ageYears,
        String mobile,
        String email,
        String bloodGroup,
        String allergies,
        String existingConditions,
        String longTermMedications,
        String surgicalHistory
) {
}
