package com.deepthoughtnet.clinic.api.prescriptiontemplate.dto;

public record PrescriptionTemplateResponse(
        String id,
        String tenantId,
        int templateVersion,
        boolean active,
        String clinicLogoDocumentId,
        String headerText,
        String footerText,
        String primaryColor,
        String accentColor,
        String disclaimer,
        String doctorSignatureText,
        boolean showQrCode,
        String watermarkText,
        String changedByAppUserId,
        String createdAt,
        String updatedAt
) {}
