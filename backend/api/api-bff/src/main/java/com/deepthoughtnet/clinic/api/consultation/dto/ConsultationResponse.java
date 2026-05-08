package com.deepthoughtnet.clinic.api.consultation.dto;

import com.deepthoughtnet.clinic.consultation.service.model.ConsultationStatus;
import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ConsultationResponse(
        String id,
        String tenantId,
        String patientId,
        String patientNumber,
        String patientName,
        String doctorUserId,
        String doctorName,
        String appointmentId,
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
        Integer respiratoryRate,
        Double bmi,
        String bmiCategory,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
