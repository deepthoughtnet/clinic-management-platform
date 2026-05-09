package com.deepthoughtnet.clinic.api.consultation.dto;

import com.deepthoughtnet.clinic.consultation.service.model.TemperatureUnit;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsultationRequest(
        @NotNull
        UUID patientId,
        @NotNull
        UUID doctorUserId,
        @NotNull
        UUID appointmentId,
        @Size(max = 2000)
        String chiefComplaints,
        @Size(max = 4000)
        String symptoms,
        @Size(max = 4000)
        String diagnosis,
        @Size(max = 8000)
        String clinicalNotes,
        @Size(max = 4000)
        String advice,
        LocalDate followUpDate,
        @Min(0) @Max(300)
        Integer bloodPressureSystolic,
        @Min(0) @Max(300)
        Integer bloodPressureDiastolic,
        @Min(0) @Max(300)
        Integer pulseRate,
        @Min(25) @Max(45)
        Double temperature,
        TemperatureUnit temperatureUnit,
        @Min(0) @Max(500)
        Double weightKg,
        @Min(0) @Max(300)
        Double heightCm,
        @Min(0) @Max(100)
        Integer spo2,
        @Min(0) @Max(120)
        Integer respiratoryRate
) {
}
