package com.deepthoughtnet.clinic.api.patientportal.dto;

import java.time.LocalDate;
import java.util.List;

public record PatientPortalPrescriptionResponse(
        String prescriptionNumber,
        LocalDate prescriptionDate,
        String doctorName,
        String clinicName,
        String diagnosisSummary,
        String adviceSummary,
        LocalDate followUpDate,
        String status,
        boolean pdfAvailable,
        List<PatientPortalPrescriptionMedicineResponse> medicines,
        List<PatientPortalPrescriptionTestResponse> recommendedTests
) {
}
