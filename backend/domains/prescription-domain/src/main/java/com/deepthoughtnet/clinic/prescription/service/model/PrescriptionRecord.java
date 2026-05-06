package com.deepthoughtnet.clinic.prescription.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PrescriptionRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String patientNumber,
        String patientName,
        UUID doctorUserId,
        String doctorName,
        UUID consultationId,
        UUID appointmentId,
        String prescriptionNumber,
        String diagnosisSnapshot,
        String advice,
        LocalDate followUpDate,
        PrescriptionStatus status,
        OffsetDateTime finalizedAt,
        OffsetDateTime printedAt,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<PrescriptionMedicineRecord> medicines,
        List<PrescriptionTestRecord> recommendedTests
) {
}
