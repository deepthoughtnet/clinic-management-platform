package com.deepthoughtnet.clinic.api.carepilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadConvertRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadNoteRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadStatusUpdateRequest;
import com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadUpsertRequest;
import com.deepthoughtnet.clinic.api.common.ClinicTimeZoneResolver;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityRecord;
import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import com.deepthoughtnet.clinic.carepilot.lead.activity.service.LeadActivityService;
import com.deepthoughtnet.clinic.carepilot.lead.analytics.LeadAnalyticsService;
import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadConversionResult;
import com.deepthoughtnet.clinic.carepilot.lead.conversion.LeadConversionService;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadPriority;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadRecord;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSearchCriteria;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadSource;
import com.deepthoughtnet.clinic.carepilot.lead.model.LeadStatus;
import com.deepthoughtnet.clinic.carepilot.lead.service.LeadService;
import com.deepthoughtnet.clinic.patient.service.model.PatientGender;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@WebMvcTest(CarePilotLeadController.class)
@Import({
        PermissionChecker.class,
        CarePilotLeadControllerAuthorizationTest.MethodSecurityConfig.class,
        CarePilotLeadControllerAuthorizationTest.ControllerConfig.class
})
class CarePilotLeadControllerAuthorizationTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockBean private LeadService leadService;
    @MockBean private LeadConversionService conversionService;
    @MockBean private LeadAnalyticsService analyticsService;
    @MockBean private LeadActivityService activityService;
    @MockBean private CarePilotLeadCsvService leadCsvService;
    @MockBean private ClinicTimeZoneResolver clinicTimeZoneResolver;

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ENGAGE_EXECUTIVE")
    void engageExecutiveCanUseLeadOperationalEndpoints() throws Exception {
        setRequestContext("ENGAGE_EXECUTIVE");
        when(clinicTimeZoneResolver.resolve(TENANT_ID)).thenReturn(ZoneId.of("Asia/Kolkata"));
        stubLeadWorkflows();

        mockMvc.perform(get("/api/carepilot/leads")).andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/leads/{id}", LEAD_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/leads")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Asha","phone":"9876543210","status":"NEW","priority":"MEDIUM","source":"MANUAL"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/carepilot/leads/{id}", LEAD_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"firstName":"Asha","phone":"9876543210","status":"CONTACTED","priority":"HIGH","source":"MANUAL"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/leads/{id}/status", LEAD_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"status":"FOLLOW_UP_REQUIRED","comment":"Checked in"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/leads/{id}/activities", LEAD_ID)).andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/leads/{id}/notes", LEAD_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"note\":\"Call back tomorrow\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/leads/{id}/convert", LEAD_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"bookAppointment\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_EXECUTIVE")
    void engageExecutiveGetsForbiddenForLeadCsvBulkEndpoints() throws Exception {
        setRequestContext("ENGAGE_EXECUTIVE");

        mockMvc.perform(get("/api/carepilot/leads/export").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/carepilot/leads/import-template").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/carepilot/leads/import-csv")
                        .file(new MockMultipartFile("file", "leads.csv", "text/csv", "firstName,phone\nAsha,9876543210\n".getBytes()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerCanUseLeadCsvBulkEndpoints() throws Exception {
        setRequestContext("ENGAGE_MANAGER");
        when(clinicTimeZoneResolver.resolve(TENANT_ID)).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(leadCsvService.importTemplateCsv()).thenReturn("firstName,phone\nAsha,9876543210\n");
        when(leadCsvService.exportCsv(eq(TENANT_ID), any(ZoneId.class), any(LeadSearchCriteria.class), any(UUID.class), anyBoolean())).thenReturn("firstName,phone\nAsha,9876543210\n");
        when(leadCsvService.importCsv(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(
                new com.deepthoughtnet.clinic.api.carepilot.dto.LeadDtos.LeadCsvImportResponse(1, 0, 0, List.of())
        );

        mockMvc.perform(get("/api/carepilot/leads/import-template"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/carepilot/leads/export"))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/carepilot/leads/import-csv")
                        .file(new MockMultipartFile("file", "leads.csv", "text/csv", "firstName,phone\nAsha,9876543210\n".getBytes()))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    private void stubLeadWorkflows() {
        LeadRecord lead = sampleLead();
        LeadActivityRecord activity = sampleActivity();
        when(leadService.search(eq(TENANT_ID), any(ZoneId.class), any(LeadSearchCriteria.class), anyInt(), anyInt(), any(), anyBoolean()))
                .thenReturn(new PageImpl<>(List.of(lead), PageRequest.of(0, 25), 1));
        when(leadService.requireVisibleLead(eq(TENANT_ID), eq(LEAD_ID), any(UUID.class), anyBoolean())).thenReturn(lead);
        when(leadService.create(eq(TENANT_ID), any(), eq(ACTOR_ID))).thenReturn(lead);
        when(leadService.update(eq(TENANT_ID), eq(LEAD_ID), any(), eq(ACTOR_ID))).thenReturn(lead);
        when(leadService.updateStatus(eq(TENANT_ID), eq(LEAD_ID), any(), eq(ACTOR_ID))).thenReturn(lead);
        when(leadService.addNote(eq(TENANT_ID), eq(LEAD_ID), any(), eq(ACTOR_ID))).thenReturn(lead);
        when(conversionService.convert(eq(TENANT_ID), eq(LEAD_ID), eq(ACTOR_ID), any(), eq(false), any(UUID.class), anyBoolean()))
                .thenReturn(new LeadConversionResult(LEAD_ID, UUID.randomUUID(), true, APPOINTMENT_ID, null));
        when(activityService.list(eq(TENANT_ID), eq(LEAD_ID), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(activity), PageRequest.of(0, 25), 1));
    }

    private LeadRecord sampleLead() {
        return new LeadRecord(
                LEAD_ID,
                TENANT_ID,
                "Asha",
                "Mehta",
                "Asha Mehta",
                "9876543210",
                "asha@example.com",
                PatientGender.UNKNOWN,
                null,
                LeadSource.MANUAL,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LeadStatus.NEW,
                LeadPriority.MEDIUM,
                "Follow up needed",
                "vip",
                null,
                null,
                OffsetDateTime.parse("2026-07-18T00:00:00Z"),
                OffsetDateTime.parse("2026-07-18T01:00:00Z"),
                OffsetDateTime.parse("2026-07-18T02:00:00Z"),
                ACTOR_ID,
                ACTOR_ID,
                OffsetDateTime.parse("2026-07-18T00:00:00Z"),
                OffsetDateTime.parse("2026-07-18T00:00:00Z")
        );
    }

    private LeadActivityRecord sampleActivity() {
        return new LeadActivityRecord(
                UUID.randomUUID(),
                TENANT_ID,
                LEAD_ID,
                LeadActivityType.STATUS_CHANGED,
                "Status changed",
                "Lead moved to follow-up",
                LeadStatus.NEW,
                LeadStatus.FOLLOW_UP_REQUIRED,
                null,
                null,
                ACTOR_ID,
                OffsetDateTime.parse("2026-07-18T00:00:00Z")
        );
    }

    private void setRequestContext(String role) {
        RequestContextHolder.set(new RequestContext(
                TenantId.of(TENANT_ID),
                ACTOR_ID,
                "tester@example.com",
                Set.of(role),
                role,
                "corr-lead-auth"
        ));
    }

    @Configuration
    static class ControllerConfig {
        @Bean
        CarePilotLeadController carePilotLeadController(
        LeadService leadService,
        LeadConversionService conversionService,
        LeadAnalyticsService analyticsService,
        LeadActivityService activityService,
        CarePilotLeadCsvService leadCsvService,
        ClinicTimeZoneResolver clinicTimeZoneResolver,
        PermissionChecker permissionChecker
        ) {
            return new CarePilotLeadController(leadService, conversionService, analyticsService, activityService, leadCsvService, clinicTimeZoneResolver, permissionChecker);
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
