package com.deepthoughtnet.clinic.vaccination.service.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record PatientVaccinationAefiCommand(
        String adverseEventStatus,
        OffsetDateTime eventDateTime,
        String onsetTimeAfterVaccination,
        String severity,
        List<String> symptoms,
        String otherSymptoms,
        String actionTaken,
        String treatmentNotes,
        String outcome,
        Boolean followUpRequired,
        LocalDate followUpDate,
        Boolean reportedToAuthority,
        String reportReferenceNumber,
        String notes
) {
}
