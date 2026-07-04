package com.deepthoughtnet.clinic.api.clinicalintake.dto;

import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ClinicalIntakeResponse(
        UUID id,
        UUID tenantId,
        UUID patientId,
        UUID appointmentId,
        UUID consultationId,
        String status,
        String chiefComplaint,
        Double heightCm,
        Double weightKg,
        Double bmi,
        String bmiCategory,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer pulseRate,
        Double temperature,
        TemperatureUnit temperatureUnit,
        Integer spo2,
        Integer respiratoryRate,
        Double randomBloodSugar,
        Integer painScore,
        String notes,
        UUID recordedByUserId,
        String recordedByName,
        boolean complete,
        OffsetDateTime completedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
