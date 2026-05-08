package com.deepthoughtnet.clinic.api.consultation.dto;

import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import java.time.LocalDate;
import java.util.UUID;

public record ConsultationRequest(
        UUID patientId,
        UUID doctorUserId,
        UUID appointmentId,
        String chiefComplaints,
        String symptoms,
        String diagnosis,
        String clinicalNotes,
        String advice,
        LocalDate followUpDate,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer pulseRate,
        Double temperature,
        TemperatureUnit temperatureUnit,
        Double weightKg,
        Double heightCm,
        Integer spo2,
        Integer respiratoryRate
) {
}
