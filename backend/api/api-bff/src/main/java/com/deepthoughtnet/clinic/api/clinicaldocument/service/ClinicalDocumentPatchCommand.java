package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import java.time.LocalDate;

public record ClinicalDocumentPatchCommand(
        ClinicalDocumentType documentType,
        String title,
        String description,
        LocalDate reportDate,
        String visibility,
        String verificationStatus
) {
}
