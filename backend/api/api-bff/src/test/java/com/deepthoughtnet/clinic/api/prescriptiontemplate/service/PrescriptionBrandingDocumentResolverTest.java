package com.deepthoughtnet.clinic.api.prescriptiontemplate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionLogoAsset;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionLogoSource;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTemplateConfig;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PrescriptionBrandingDocumentResolverTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID LOGO_ID = UUID.randomUUID();

    @Test
    void resolvesActiveTemplateLogoIntoDocumentBytes() {
        PrescriptionTemplateService templateService = mock(PrescriptionTemplateService.class);
        PrescriptionBrandingDocumentResolver resolver = new PrescriptionBrandingDocumentResolver(templateService);
        PrescriptionTemplateRecord active = record(LOGO_ID);
        byte[] bytes = new byte[] {1, 2, 3};

        when(templateService.getActive(TENANT_ID)).thenReturn(active);
        when(templateService.toPdfConfig(active)).thenReturn(new PrescriptionTemplateConfig(LOGO_ID.toString(), null, null, "#0f766e", "#14b8a6", null, null, true, null));
        when(templateService.resolve(TENANT_ID, LOGO_ID)).thenReturn(Optional.of(new PrescriptionLogoAsset(bytes, "image/png", "clinic-logo.png")));

        var document = resolver.resolveActive(TENANT_ID);

        assertThat(document.logoSource()).isEqualTo(PrescriptionLogoSource.CUSTOM);
        assertThat(document.logoBytes()).isEqualTo(bytes);
        assertThat(document.logoContentType()).isEqualTo("image/png");
        assertThat(document.logoFileName()).isEqualTo("clinic-logo.png");
    }

    @Test
    void keepsDefaultPlaceholderWhenNoLogoConfigured() {
        PrescriptionTemplateService templateService = mock(PrescriptionTemplateService.class);
        PrescriptionBrandingDocumentResolver resolver = new PrescriptionBrandingDocumentResolver(templateService);
        PrescriptionTemplateRecord active = record(null);

        when(templateService.getActive(TENANT_ID)).thenReturn(active);
        when(templateService.toPdfConfig(active)).thenReturn(PrescriptionTemplateConfig.defaults());

        var document = resolver.resolveActive(TENANT_ID);

        assertThat(document.logoSource()).isEqualTo(PrescriptionLogoSource.DEFAULT);
        assertThat(document.logoBytes()).isNull();
        assertThat(document.logoFallbackReason()).isEqualTo("NOT_CONFIGURED");
    }

    private PrescriptionTemplateRecord record(UUID logoId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new PrescriptionTemplateRecord(
                UUID.randomUUID(),
                TENANT_ID,
                41,
                true,
                logoId,
                null,
                null,
                "#0f766e",
                "#14b8a6",
                null,
                null,
                true,
                null,
                UUID.randomUUID(),
                now,
                now
        );
    }
}
