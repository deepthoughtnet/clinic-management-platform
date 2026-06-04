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
        String chronicConditions,
        String longTermMedications
) {
}
