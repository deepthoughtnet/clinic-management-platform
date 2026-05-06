package com.deepthoughtnet.clinic.prescription.service.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PrescriptionUpsertCommand(
        UUID patientId,
        UUID doctorUserId,
        UUID consultationId,
        UUID appointmentId,
        String diagnosisSnapshot,
        String advice,
        LocalDate followUpDate,
        List<PrescriptionMedicineCommand> medicines,
        List<PrescriptionTestCommand> recommendedTests
) {
}
