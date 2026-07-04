package com.deepthoughtnet.clinic.api.clinicalintake.dto;

import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import java.util.UUID;

public record ClinicalIntakeRequest(
        UUID appointmentId,
        UUID consultationId,
        String chiefComplaint,
        Double heightCm,
        Double weightKg,
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
        boolean complete
) {
}
