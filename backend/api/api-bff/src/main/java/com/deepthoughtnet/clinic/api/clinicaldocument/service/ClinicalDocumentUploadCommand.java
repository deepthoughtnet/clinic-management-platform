package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import java.time.LocalDate;
import java.util.UUID;

public record ClinicalDocumentUploadCommand(
        UUID tenantId,
        UUID patientId,
        UUID consultationId,
        UUID uploadedByAppUserId,
        ClinicalDocumentType documentType,
        String title,
        LocalDate reportDate,
        String uploadSource,
        String sourceModule,
        String sourceEntityId,
        String visibility,
        String originalFilename,
        String mediaType,
        byte[] bytes,
        String notes
) {
}
