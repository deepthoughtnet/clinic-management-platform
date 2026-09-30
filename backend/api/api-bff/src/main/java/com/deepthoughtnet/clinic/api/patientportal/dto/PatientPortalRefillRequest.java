package com.deepthoughtnet.clinic.api.patientportal.dto;

import jakarta.validation.constraints.NotBlank;

public record PatientPortalRefillRequest(@NotBlank String prescriptionNumber) { }
