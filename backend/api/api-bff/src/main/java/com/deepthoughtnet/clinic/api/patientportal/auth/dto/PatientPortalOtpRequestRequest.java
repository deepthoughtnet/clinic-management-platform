package com.deepthoughtnet.clinic.api.patientportal.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record PatientPortalOtpRequestRequest(
        @JsonAlias("phone")
        @NotBlank String mobile,
        @Valid PatientPortalOtpContext context
) {
}
