package com.deepthoughtnet.clinic.api.lab;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.errors.GlobalRestExceptionHandler;
import com.deepthoughtnet.clinic.api.lab.dto.LabCategoryConfigDtos.LabCategoryConfigResponse;
import com.deepthoughtnet.clinic.api.lab.service.LabService;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderCreateCommand;
import com.deepthoughtnet.clinic.api.lab.service.model.LabOrderRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

    @Test
    void createConsultationOrderAcceptsPatientIdTestIdsAndNotes() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        LabService labService = mock(LabService.class);
        when(labService.createOrderFromConsultation(
                org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(consultationId),
                org.mockito.ArgumentMatchers.any(LabOrderCreateCommand.class),
                org.mockito.ArgumentMatchers.eq(actorId)
        )).thenReturn(sampleOrderRecord(tenantId, consultationId, patientId));
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LabController(labService, mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(post("/api/lab/consultations/{consultationId}/orders", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "patientId", patientId,
                                "testIds", List.of(testId),
                                "notes", "Consultation request"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void createConsultationOrderReturnsFieldValidationForMissingPatientId() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID consultationId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        RequestContextHolder.set(new RequestContext(TenantId.of(tenantId), actorId, "sub", Set.of("DOCTOR"), "DOCTOR", "cid"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LabController(mock(LabService.class), mock(LabCsvService.class), mock(LabCatalogueConfigService.class)))
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .build();

        mockMvc.perform(post("/api/lab/consultations/{consultationId}/orders", consultationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "testIds", List.of(testId),
                                "notes", "Consultation request"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("patientId")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Patient is required")));
    }

    private LabOrderRecord sampleOrderRecord(UUID tenantId, UUID consultationId, UUID patientId) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", UUID.randomUUID());
        data.put("tenantId", tenantId);
        data.put("orderNumber", "LAB-20260703-0001");
        data.put("patientId", patientId);
        data.put("patientNumber", "PAT-001");
        data.put("patientName", "Demo Patient");
        data.put("consultationId", consultationId);
        data.put("orderOrigin", "CONSULTATION");
        data.put("notes", "Consultation request");
        data.put("status", "ORDERED");
        data.put("attachments", List.of());
        data.put("items", List.of());
        data.put("samples", List.of());
        data.put("results", List.of());
        return new ObjectMapper().convertValue(data, LabOrderRecord.class);
    }
}
