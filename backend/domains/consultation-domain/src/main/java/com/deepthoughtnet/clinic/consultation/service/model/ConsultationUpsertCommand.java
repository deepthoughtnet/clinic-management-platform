package com.deepthoughtnet.clinic.consultation.service.model;

import java.time.LocalDate;
import java.util.UUID;

public record ConsultationUpsertCommand(
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
