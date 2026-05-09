package com.deepthoughtnet.clinic.api.doctor.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DoctorProfileRequest(
        @Size(max = 32) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "mobile contains invalid characters")
        String mobile,
        @Size(max = 128) String specialization,
        @Size(max = 256) String qualification,
        @Size(max = 128) String registrationNumber,
        @Size(max = 128) String consultationRoom,
        Boolean active
) {
}
