package com.deepthoughtnet.clinic.api.doctor.dto;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

public record DoctorProfileRequest(
        @Size(max = 32) @Pattern(regexp = "^[0-9+\\-\\s]*$", message = "mobile contains invalid characters")
        String mobile,
        @Size(max = 128) String specialization,
        @Size(max = 10) List<@Size(max = 128) String> specializations,
        @Size(max = 256) String qualification,
        @Size(max = 128) String registrationNumber,
        @Size(max = 128) String consultationRoom,
        @PositiveOrZero BigDecimal consultationFee,
        @PositiveOrZero BigDecimal opdFee,
        @PositiveOrZero BigDecimal followUpFee,
        @PositiveOrZero BigDecimal emergencyFee,
        @PositiveOrZero Integer yearsOfExperience,
        @PositiveOrZero @Max(120) Integer age,
        Boolean active,
        Boolean publicListingEnabled,
        @Size(max = 192) String slug
) {
}
