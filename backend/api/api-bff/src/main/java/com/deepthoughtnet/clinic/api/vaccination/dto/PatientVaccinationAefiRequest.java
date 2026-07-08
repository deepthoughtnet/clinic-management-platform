package com.deepthoughtnet.clinic.api.vaccination.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PatientVaccinationAefiRequest(
        @Size(max = 32)
        String adverseEventStatus,
        OffsetDateTime eventDateTime,
        @Size(max = 128)
        String onsetTimeAfterVaccination,
        @Size(max = 32)
        String severity,
        List<@Size(max = 64) String> symptoms,
        @Size(max = 250)
        String otherSymptoms,
        @Size(max = 128)
        String actionTaken,
        @Size(max = 1000)
        String treatmentNotes,
        @Size(max = 64)
        String outcome,
        Boolean followUpRequired,
        LocalDate followUpDate,
        Boolean reportedToAuthority,
        @Size(max = 128)
        String reportReferenceNumber,
        @Size(max = 1000)
        String notes
) {
}
