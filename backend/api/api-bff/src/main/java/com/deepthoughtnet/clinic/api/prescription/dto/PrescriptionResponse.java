package com.deepthoughtnet.clinic.api.prescription.dto;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionMedicineRecord;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionStatus;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTestRecord;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PrescriptionResponse(
        String id,
        String tenantId,
        String patientId,
        String patientNumber,
        String patientName,
        String doctorUserId,
        String doctorName,
        String consultationId,
        String appointmentId,
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
