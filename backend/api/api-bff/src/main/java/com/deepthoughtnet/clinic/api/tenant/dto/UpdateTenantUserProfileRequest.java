package com.deepthoughtnet.clinic.api.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantUserProfileRequest(
        @NotBlank(message = "Name is required.")
        @Size(max = 256, message = "Name must be 256 characters or fewer.")
        String displayName,
        @Email(message = "Enter a valid email address.")
        @Size(max = 256, message = "Email must be 256 characters or fewer.")
        String email,
        @Size(max = 128, message = "Login ID must be 128 characters or fewer.")
        String username,
        @Size(max = 64, message = "Employee code must be 64 characters or fewer.")
        String employeeCode,
        @Pattern(regexp = "^[0-9]{10}$", message = "Enter a valid 10-digit mobile number.")
        String mobile,
        @Size(max = 128, message = "Department must be 128 characters or fewer.")
        String department,
        @Size(max = 64, message = "Role must be 64 characters or fewer.")
        String role,
        boolean active
) {
}
