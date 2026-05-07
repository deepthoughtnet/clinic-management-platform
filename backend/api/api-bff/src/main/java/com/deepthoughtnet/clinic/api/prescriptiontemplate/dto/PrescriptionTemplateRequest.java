package com.deepthoughtnet.clinic.api.prescriptiontemplate.dto;

public record PrescriptionTemplateRequest(
        String clinicLogoDocumentId,
        String headerText,
        String footerText,
        String primaryColor,
        String accentColor,
        String disclaimer,
        String doctorSignatureText,
        Boolean showQrCode,
        String watermarkText
) {}
