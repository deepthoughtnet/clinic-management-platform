package com.deepthoughtnet.clinic.api.prescription.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PrescriptionRequest(
        UUID patientId,
        UUID doctorUserId,
        UUID consultationId,
        UUID appointmentId,
        String diagnosisSnapshot,
        String advice,
        LocalDate followUpDate,
        List<PrescriptionMedicineRequest> medicines,
        List<PrescriptionTestRequest> recommendedTests
) {
}
