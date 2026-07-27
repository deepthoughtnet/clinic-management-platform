package com.deepthoughtnet.clinic.prescription.service.model;

import java.util.Arrays;

public record PrescriptionBrandingDocument(
        String headerText,
        String footerText,
        String primaryColor,
        String accentColor,
        String disclaimer,
        String doctorSignatureText,
        boolean showQrCode,
        String watermarkText,
        byte[] logoBytes,
        String logoContentType,
        String logoFileName,
        PrescriptionLogoSource logoSource,
        String logoFallbackReason
) {
    public PrescriptionBrandingDocument {
        logoBytes = logoBytes == null ? null : Arrays.copyOf(logoBytes, logoBytes.length);
        logoSource = logoSource == null ? PrescriptionLogoSource.DEFAULT : logoSource;
    }

    public byte[] logoBytes() {
        return logoBytes == null ? null : Arrays.copyOf(logoBytes, logoBytes.length);
    }

    public PrescriptionTemplateConfig templateConfig() {
        return new PrescriptionTemplateConfig(
                null,
                headerText,
                footerText,
                primaryColor,
                accentColor,
                disclaimer,
                doctorSignatureText,
                showQrCode,
                watermarkText
        );
    }

    public static PrescriptionBrandingDocument defaults() {
        return fromTemplateConfig(PrescriptionTemplateConfig.defaults());
    }

    public static PrescriptionBrandingDocument fromTemplateConfig(PrescriptionTemplateConfig config) {
        PrescriptionTemplateConfig safe = config == null ? PrescriptionTemplateConfig.defaults() : config;
        return new PrescriptionBrandingDocument(
                safe.headerText(),
                safe.footerText(),
                safe.primaryColor(),
                safe.accentColor(),
                safe.disclaimer(),
                safe.doctorSignatureText(),
                safe.showQrCode(),
                safe.watermarkText(),
                null,
                null,
                null,
                PrescriptionLogoSource.DEFAULT,
                "NOT_CONFIGURED"
        );
    }

    public static PrescriptionBrandingDocument withLogo(PrescriptionTemplateConfig config, PrescriptionLogoAsset asset) {
        PrescriptionTemplateConfig safe = config == null ? PrescriptionTemplateConfig.defaults() : config;
        if (asset == null || asset.bytes() == null || asset.bytes().length == 0) {
            return fromTemplateConfig(safe);
        }
        return new PrescriptionBrandingDocument(
                safe.headerText(),
                safe.footerText(),
                safe.primaryColor(),
                safe.accentColor(),
                safe.disclaimer(),
                safe.doctorSignatureText(),
                safe.showQrCode(),
                safe.watermarkText(),
                asset.bytes(),
                asset.contentType(),
                asset.fileName(),
                PrescriptionLogoSource.CUSTOM,
                null
        );
    }
}
