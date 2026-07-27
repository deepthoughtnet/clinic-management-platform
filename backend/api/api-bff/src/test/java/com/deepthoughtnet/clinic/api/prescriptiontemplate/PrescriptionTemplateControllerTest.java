package com.deepthoughtnet.clinic.api.prescriptiontemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateRecord;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionBrandingDocumentResolver;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.db.PrescriptionTemplateSettings;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionBrandingDocument;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionPdf;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionTemplateConfig;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PrescriptionTemplateControllerTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private PrescriptionTemplateService templateService;
    private PrescriptionBrandingDocumentResolver brandingDocumentResolver;
    private PrescriptionService prescriptionService;
    private PrescriptionTemplateController controller;

    @BeforeEach
    void setUp() {
        templateService = mock(PrescriptionTemplateService.class);
        brandingDocumentResolver = mock(PrescriptionBrandingDocumentResolver.class);
        prescriptionService = mock(PrescriptionService.class);
        controller = new PrescriptionTemplateController(templateService, brandingDocumentResolver, prescriptionService);
        RequestContextHolder.set(new RequestContext(
                TenantId.of(TENANT_ID),
                ACTOR_ID,
                "admin@clinic.local",
                Set.of("CLINIC_ADMIN"),
                "CLINIC_ADMIN",
                "test-correlation"
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void getActiveIncludesBrowserSafeLogoUrl() {
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-26T10:00:00Z");
        when(templateService.getActive(TENANT_ID)).thenReturn(new PrescriptionTemplateRecord(
                UUID.randomUUID(),
                TENANT_ID,
                7,
                true,
                UUID.fromString("947ed4f4-e03e-4907-bf4a-f82f9e2ab60b"),
                "logo",
                "logo-footer",
                "#0f766e",
                "#14b8a6",
                null,
                "Dr. Jeevan",
                true,
                null,
                ACTOR_ID,
                updatedAt,
                updatedAt
        ));

        var response = controller.getActive();

        assertThat(response.clinicLogoDocumentId()).isEqualTo("947ed4f4-e03e-4907-bf4a-f82f9e2ab60b");
        assertThat(response.logoUrl()).isEqualTo("/api/settings/prescription-template/logo?v=1785060000000");
    }

    @Test
    void getActiveLeavesLogoUrlNullWhenLogoMissing() {
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-26T10:00:00Z");
        when(templateService.getActive(TENANT_ID)).thenReturn(new PrescriptionTemplateRecord(
                UUID.randomUUID(),
                TENANT_ID,
                7,
                true,
                null,
                "logo",
                "logo-footer",
                "#0f766e",
                "#14b8a6",
                null,
                "Dr. Jeevan",
                true,
                null,
                ACTOR_ID,
                updatedAt,
                updatedAt
        ));

        var response = controller.getActive();

        assertThat(response.logoUrl()).isNull();
    }

    @Test
    void previewMergesPersistedLogoWhenRequestOmitsLogoField() {
        UUID logoDocumentId = UUID.fromString("947ed4f4-e03e-4907-bf4a-f82f9e2ab60b");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-26T10:00:00Z");
        when(templateService.getActive(TENANT_ID)).thenReturn(new PrescriptionTemplateRecord(
                UUID.randomUUID(),
                TENANT_ID,
                7,
                true,
                logoDocumentId,
                "logo",
                "logo-footer",
                "#0f766e",
                "#14b8a6",
                null,
                "Dr. Jeevan",
                true,
                null,
                ACTOR_ID,
                updatedAt,
                updatedAt
        ));
        when(brandingDocumentResolver.resolve(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.any(PrescriptionTemplateRecord.class)
        )).thenAnswer(invocation -> {
            PrescriptionTemplateRecord record = invocation.getArgument(1);
            return PrescriptionBrandingDocument.fromTemplateConfig(new PrescriptionTemplateConfig(
                    record.clinicLogoDocumentId() == null ? null : record.clinicLogoDocumentId().toString(),
                    record.headerText(),
                    record.footerText(),
                    record.primaryColor(),
                    record.accentColor(),
                    record.disclaimer(),
                    record.doctorSignatureText(),
                    record.showQrCode(),
                    record.watermarkText()
            ));
        });
        when(prescriptionService.generateTemplatePreviewPdf(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID),
                org.mockito.ArgumentMatchers.any(PrescriptionBrandingDocument.class)
        )).thenReturn(new PrescriptionPdf("preview.pdf", new byte[]{1, 2, 3}));

        controller.preview(new com.deepthoughtnet.clinic.api.prescriptiontemplate.dto.PrescriptionTemplateRequest(
                null,
                "header",
                "footer",
                "#0f766e",
                "#14b8a6",
                null,
                "Dr. Jeevan",
                true,
                null
        ), null);

        ArgumentCaptor<PrescriptionTemplateRecord> captor = ArgumentCaptor.forClass(PrescriptionTemplateRecord.class);
        verify(brandingDocumentResolver).resolve(org.mockito.ArgumentMatchers.eq(TENANT_ID), captor.capture());
        assertThat(captor.getValue().clinicLogoDocumentId()).isEqualTo(logoDocumentId);
    }

    @Test
    void savePreservesPersistedLogoWhenRequestOmitsLogoField() {
        UUID logoDocumentId = UUID.fromString("947ed4f4-e03e-4907-bf4a-f82f9e2ab60b");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-26T10:00:00Z");
        when(templateService.getActive(TENANT_ID)).thenReturn(new PrescriptionTemplateRecord(
                UUID.randomUUID(),
                TENANT_ID,
                41,
                true,
                logoDocumentId,
                "logo",
                "logo-footer",
                "#0f766e",
                "#14b8a6",
                null,
                "Dr. Jeevan",
                true,
                null,
                ACTOR_ID,
                updatedAt,
                updatedAt
        ));
        when(templateService.save(org.mockito.ArgumentMatchers.eq(TENANT_ID), org.mockito.ArgumentMatchers.eq(ACTOR_ID), org.mockito.ArgumentMatchers.any(PrescriptionTemplateSettings.class)))
                .thenAnswer(invocation -> {
                    PrescriptionTemplateSettings settings = invocation.getArgument(2);
                    return new PrescriptionTemplateRecord(
                            UUID.randomUUID(),
                            TENANT_ID,
                            42,
                            true,
                            settings.clinicLogoDocumentId(),
                            settings.headerText(),
                            settings.footerText(),
                            settings.primaryColor(),
                            settings.accentColor(),
                            settings.disclaimer(),
                            settings.doctorSignatureText(),
                            settings.showQrCode(),
                            settings.watermarkText(),
                            ACTOR_ID,
                            updatedAt,
                            updatedAt
                    );
                });

        controller.save(new com.deepthoughtnet.clinic.api.prescriptiontemplate.dto.PrescriptionTemplateRequest(
                null,
                "header",
                "footer",
                "#0f766e",
                "#14b8a6",
                null,
                "Dr. Jeevan",
                true,
                null
        ));

        ArgumentCaptor<PrescriptionTemplateSettings> captor = ArgumentCaptor.forClass(PrescriptionTemplateSettings.class);
        verify(templateService).save(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(ACTOR_ID),
                captor.capture()
        );
        assertThat(captor.getValue().clinicLogoDocumentId()).isEqualTo(logoDocumentId);
    }
}
