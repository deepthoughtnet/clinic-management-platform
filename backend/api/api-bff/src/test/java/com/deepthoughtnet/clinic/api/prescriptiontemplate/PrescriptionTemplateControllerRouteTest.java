package com.deepthoughtnet.clinic.api.prescriptiontemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateRecord;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionBrandingDocumentResolver;
import com.deepthoughtnet.clinic.api.prescriptiontemplate.service.PrescriptionTemplateService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.deepthoughtnet.clinic.prescription.service.PrescriptionService;
import com.deepthoughtnet.clinic.prescription.service.model.PrescriptionLogoAsset;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PrescriptionTemplateControllerRouteTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private PrescriptionTemplateService templateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        templateService = mock(PrescriptionTemplateService.class);
        PrescriptionTemplateController controller = new PrescriptionTemplateController(templateService, mock(PrescriptionBrandingDocumentResolver.class), mock(PrescriptionService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
    void getLogoStreamsBytesEvenWhenClientSendsGenericJsonAcceptHeader() throws Exception {
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-07-26T10:00:00Z");
        when(templateService.getActive(TENANT_ID)).thenReturn(new PrescriptionTemplateRecord(
                UUID.randomUUID(),
                TENANT_ID,
                7,
                true,
                DOCUMENT_ID,
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
        when(templateService.resolve(TENANT_ID, DOCUMENT_ID)).thenReturn(java.util.Optional.of(new PrescriptionLogoAsset(
                new byte[] {1, 2, 3, 4},
                null,
                "clinic-logo.png"
        )));

        mockMvc.perform(get("/api/settings/prescription-template/logo").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("clinic-logo.png")))
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));
    }
}
