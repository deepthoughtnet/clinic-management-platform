package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;

public record PatientPortalMeResponse(
        String patientNumber,
        String fullName,
        String clinicName,
        String gender,
        LocalDate dateOfBirth,
        Integer ageYears,
        String mobile,
        String email,
        String bloodGroup,
        String allergies,
        String chronicConditions,
        String longTermMedications
) {
}
