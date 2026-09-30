package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PatientPortalRefillResponse(
        UUID requestId, String prescriptionNumber, String medicineSummary,
        LocalDate dueDate, String status, String source, OffsetDateTime createdAt
) { }
