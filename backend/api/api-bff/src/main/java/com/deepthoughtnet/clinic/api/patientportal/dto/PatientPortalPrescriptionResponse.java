package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PatientPortalPrescriptionResponse(
        String prescriptionId,
        String prescriptionNumber,
        Integer versionNumber,
        String doctorName,
        String diagnosisSnapshot,
        String advice,
        LocalDate followUpDate,
        String status,
        OffsetDateTime finalizedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<PatientPortalPrescriptionMedicineResponse> medicines,
        List<PatientPortalPrescriptionTestResponse> recommendedTests
) {
}
