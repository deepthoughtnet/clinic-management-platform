package com.deepthoughtnet.clinic.api.prescriptiontemplate.service;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionBrandingDocument;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionLogoAsset;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionLogoSource;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTemplateConfig;
import com.deepthoughtnet.clinic.platform.branding.BrandingLogoAsset;
import com.deepthoughtnet.clinic.platform.branding.BrandingLogoProvider;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrescriptionBrandingDocumentResolver implements BrandingLogoProvider {
    private static final Logger log = LoggerFactory.getLogger(PrescriptionBrandingDocumentResolver.class);

    private final PrescriptionTemplateService templateService;

    public PrescriptionBrandingDocumentResolver(PrescriptionTemplateService templateService) {
        this.templateService = templateService;
    }

    @Transactional(readOnly = true)
    public PrescriptionBrandingDocument resolveActive(UUID tenantId) {
        return resolve(tenantId, templateService.getActive(tenantId));
    }

    @Transactional(readOnly = true)
    public PrescriptionBrandingDocument resolve(UUID tenantId, PrescriptionTemplateRecord record) {
        PrescriptionTemplateConfig config = templateService.toPdfConfig(record);
        UUID logoDocumentId = record == null ? null : record.clinicLogoDocumentId();
        if (logoDocumentId == null) {
            log.debug("prescription.branding.logo.document tenantId={} templateVersion={} selectedSource=DEFAULT fallbackReason=NOT_CONFIGURED",
                    tenantId, record == null ? null : record.templateVersion());
            return document(config, null, "NOT_CONFIGURED");
        }

        Optional<PrescriptionLogoAsset> asset = templateService.resolve(tenantId, logoDocumentId);
        if (asset.isEmpty()) {
            log.warn("prescription.branding.logo.document tenantId={} templateVersion={} logoDocumentId={} selectedSource=DEFAULT fallbackReason=MEDIA_MISSING",
                    tenantId, record.templateVersion(), logoDocumentId);
            return document(config, null, "MEDIA_MISSING");
        }

        PrescriptionLogoAsset logo = asset.get();
        log.debug(
                "prescription.branding.logo.document tenantId={} templateVersion={} logoDocumentId={} selectedSource=CUSTOM contentType={} byteCount={}",
                tenantId,
                record.templateVersion(),
                logoDocumentId,
                logo.contentType(),
                logo.bytes() == null ? 0 : logo.bytes().length
        );
        return document(config, logo, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrandingLogoAsset> resolveLogo(UUID tenantId) {
        PrescriptionBrandingDocument branding = resolveActive(tenantId);
        if (branding == null || branding.logoBytes() == null || branding.logoBytes().length == 0) {
            return Optional.empty();
        }
        return Optional.of(new BrandingLogoAsset(branding.logoBytes(), branding.logoContentType(), branding.logoFileName()));
    }

    private PrescriptionBrandingDocument document(PrescriptionTemplateConfig config, PrescriptionLogoAsset logo, String fallbackReason) {
        PrescriptionTemplateConfig safe = config == null ? PrescriptionTemplateConfig.defaults() : config;
        if (logo != null && logo.bytes() != null && logo.bytes().length > 0) {
            return new PrescriptionBrandingDocument(
                    safe.headerText(),
                    safe.footerText(),
                    safe.primaryColor(),
                    safe.accentColor(),
                    safe.disclaimer(),
                    safe.doctorSignatureText(),
                    safe.showQrCode(),
                    safe.watermarkText(),
                    logo.bytes(),
                    logo.contentType(),
                    logo.fileName(),
                    PrescriptionLogoSource.CUSTOM,
                    null
            );
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
                null,
                null,
                null,
                PrescriptionLogoSource.DEFAULT,
                fallbackReason == null ? "NOT_CONFIGURED" : fallbackReason
        );
    }
}
