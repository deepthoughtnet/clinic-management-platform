package com.deepthoughtnet.clinic.api.lab;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.LabCatalogueConfigService;
import com.deepthoughtnet.clinic.api.lab.dto.LabCategoryConfigDtos.LabCategoryConfigResponse;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class LabControllerRouteTest {

    @AfterEach
    void clear() {
        RequestContextHolder.clear();
    }

    @Test
    void importTemplateUsesCsvNegotiationAndAttachmentHeaders() throws Exception {
        LabCsvService labCsvService = mock(LabCsvService.class);
        when(labCsvService.importTemplateCsv()).thenReturn("testCode,testName\nCBC,Complete Blood Count\n");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LabController(mock(LabService.class), labCsvService, mock(LabCatalogueConfigService.class))).build();

        mockMvc.perform(get("/api/lab/tests/import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("lab-tests-import-template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("testCode,testName")));
    }

    @Test
    void listCategoryConfigUsesTenantScope() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LabCatalogueConfigService configService = mock(LabCatalogueConfigService.class);
        when(configService.listCategories(tenantId)).thenReturn(List.of(new LabCategoryConfigResponse("HEMATOLOGY", "Hematology", true, 1)));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("CLINIC_ADMIN"), "CLINIC_ADMIN", "cid"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LabController(mock(LabService.class), mock(LabCsvService.class), configService)).build();

        mockMvc.perform(get("/api/lab/config/categories"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HEMATOLOGY")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hematology")));
    }
}
