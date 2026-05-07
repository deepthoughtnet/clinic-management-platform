package com.deepthoughtnet.clinic.prescription.service.model;

public record PrescriptionTemplateConfig(
        String clinicLogoDocumentId,
        String headerText,
        String footerText,
        String primaryColor,
        String accentColor,
        String disclaimer,
        String doctorSignatureText,
        boolean showQrCode,
        String watermarkText
) {
    public static PrescriptionTemplateConfig defaults() {
        return new PrescriptionTemplateConfig(null, null, null, "#0f766e", "#14b8a6", null, null, true, null);
    }
}
