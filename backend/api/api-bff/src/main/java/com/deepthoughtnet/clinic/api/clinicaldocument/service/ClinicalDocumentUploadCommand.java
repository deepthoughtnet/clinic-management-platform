package com.deepthoughtnet.clinic.api.clinicaldocument.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.db.ClinicalDocumentType;
import java.util.UUID;

public record ClinicalDocumentUploadCommand(
        UUID tenantId,
        UUID patientId,
        UUID consultationId,
        UUID appointmentId,
        UUID uploadedByAppUserId,
        ClinicalDocumentType documentType,
        String originalFilename,
        String mediaType,
        byte[] bytes,
        String notes,
        String referredDoctor,
        String referredHospital,
        String referralNotes
) {
}
