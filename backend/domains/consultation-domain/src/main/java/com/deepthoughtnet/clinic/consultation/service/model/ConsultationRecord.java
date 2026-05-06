package com.deepthoughtnet.clinic.consultation.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ConsultationRecord(
        UUID id,
        UUID tenantId,
        UUID patientId,
        String patientNumber,
        String patientName,
        UUID doctorUserId,
        String doctorName,
        UUID appointmentId,
        String chiefComplaints,
        String symptoms,
        String diagnosis,
        String clinicalNotes,
        String advice,
        LocalDate followUpDate,
        ConsultationStatus status,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer pulseRate,
        Double temperature,
        TemperatureUnit temperatureUnit,
        Double weightKg,
        Double heightCm,
        Integer spo2,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
