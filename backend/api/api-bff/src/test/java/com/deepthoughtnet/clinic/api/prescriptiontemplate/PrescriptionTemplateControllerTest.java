package com.deepthoughtnet.clinic.api.prescriptiontemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateRecord;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrescriptionTemplateControllerTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private PrescriptionTemplateService templateService;
    private PrescriptionTemplateController controller;

    @BeforeEach
    void setUp() {
        templateService = mock(PrescriptionTemplateService.class);
        controller = new PrescriptionTemplateController(templateService, mock(PrescriptionService.class));
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
}
