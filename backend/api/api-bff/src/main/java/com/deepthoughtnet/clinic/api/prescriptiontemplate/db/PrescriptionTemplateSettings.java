package com.deepthoughtnet.clinic.api.prescriptiontemplate.db;

import java.util.UUID;

public record PrescriptionTemplateSettings(
        UUID clinicLogoDocumentId,
        String headerText,
        String footerText,
        String primaryColor,
        String accentColor,
        String disclaimer,
        String doctorSignatureText,
        boolean showQrCode,
        String watermarkText
) {}
