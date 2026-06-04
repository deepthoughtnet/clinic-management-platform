package com.deepthoughtnet.clinic.api.patientportal.dto;

import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PatientPortalProfileUpdateRequest(
        @NotBlank @Size(max = 128)
        String firstName,
        @NotBlank @Size(max = 128)
        String lastName,
        @NotNull
        PatientGender gender,
        @PastOrPresent
        LocalDate dateOfBirth,
        @PositiveOrZero @Max(130)
        Integer ageYears,
        @Email @Size(max = 255)
        String email,
        @Size(max = 255)
        String addressLine1,
        @Size(max = 255)
        String addressLine2,
        @Size(max = 120)
        String city,
        @Size(max = 120)
        String state,
        @Size(max = 120)
        String country,
        @Size(max = 20)
        String postalCode,
        @Size(max = 128)
        String emergencyContactName,
        @Size(max = 24)
        String emergencyContactMobile
) {
}
