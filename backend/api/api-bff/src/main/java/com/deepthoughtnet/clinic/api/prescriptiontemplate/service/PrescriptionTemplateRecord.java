package com.deepthoughtnet.clinic.api.prescriptiontemplate.service;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PrescriptionTemplateRecord(
        UUID id,
        UUID tenantId,
        int templateVersion,
        boolean active,
        UUID clinicLogoDocumentId,
        String headerText,
        String footerText,
        String primaryColor,
        String accentColor,
        String disclaimer,
        String doctorSignatureText,
        boolean showQrCode,
        String watermarkText,
        UUID changedByAppUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
