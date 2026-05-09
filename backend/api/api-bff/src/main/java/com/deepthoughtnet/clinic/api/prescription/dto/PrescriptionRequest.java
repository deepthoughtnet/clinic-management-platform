package com.deepthoughtnet.clinic.api.prescription.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrescriptionRequest(
        @NotNull
        UUID patientId,
        @NotNull
        UUID doctorUserId,
        @NotNull
        UUID consultationId,
        UUID appointmentId,
        @Size(max = 4000)
        String diagnosisSnapshot,
        @Size(max = 4000)
        String advice,
        LocalDate followUpDate,
        @NotEmpty @Valid
        List<PrescriptionMedicineRequest> medicines,
        @Valid
        List<PrescriptionTestRequest> recommendedTests
) {
}
