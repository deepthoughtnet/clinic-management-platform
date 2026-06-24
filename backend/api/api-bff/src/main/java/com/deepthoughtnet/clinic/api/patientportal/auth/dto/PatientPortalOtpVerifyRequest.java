package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record PatientPortalOtpVerifyRequest(
        @JsonAlias("phone")
        @NotBlank String mobile,
        @NotBlank String otp,
        @Valid PatientPortalOtpContext context
) {
}
