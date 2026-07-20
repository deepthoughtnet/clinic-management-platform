package com.deepthoughtnet.clinic.api.carepilot;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.carepilot.engagement.analytics.PatientEngagementOverviewRecord;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType;
import com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementLevel;
import com.deepthoughtnet.clinic.carepilot.engagement.model.PatientEngagementProfileRecord;
import com.deepthoughtnet.clinic.carepilot.engagement.model.RiskLevel;
import com.deepthoughtnet.clinic.carepilot.engagement.service.PatientEngagementService;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CarePilotEngagementControllerRouteTest {
    private final UUID tenantId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    void overviewAndCohortsExposeBusinessFacingCountsAndRows() throws Exception {
        PatientEngagementService service = org.mockito.Mockito.mock(PatientEngagementService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CarePilotEngagementController(service)).build();

        PatientEngagementProfileRecord profile = new PatientEngagementProfileRecord(
                UUID.randomUUID(),
                tenantId,
                "PAT-1001",
                "Jane Doe",
                "jane@example.com",
                "+10000000000",
                88,
                EngagementLevel.HIGH,
                RiskLevel.LOW,
                RiskLevel.LOW,
                RiskLevel.LOW,
                RiskLevel.LOW,
                RiskLevel.LOW,
                RiskLevel.LOW,
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 18),
                OffsetDateTime.parse("2026-07-19T10:00:00Z"),
                0,
                4,
                0,
                0,
                0,
                0,
                false,
                List.of("No recent appointment/consultation activity for 5 days"),
                "APPOINTMENT_REMINDER",
                OffsetDateTime.parse("2026-07-19T10:15:00Z")
        );

        when(service.overview(tenantId)).thenReturn(new PatientEngagementOverviewRecord(
                1,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Map.of("HIGH", 1L),
                Map.of("HIGH_RISK_PATIENTS", 1L),
                OffsetDateTime.parse("2026-07-19T10:15:00Z")
        ));
        when(service.cohort(tenantId, EngagementCohortType.HIGH_RISK_PATIENTS, 0, 200)).thenReturn(List.of(profile));
        when(service.cohortCount(tenantId, EngagementCohortType.HIGH_RISK_PATIENTS)).thenReturn(1L);
        when(service.profiles(tenantId, EngagementLevel.HIGH, 0, 200)).thenReturn(List.of(profile));
        when(service.profileCount(tenantId, EngagementLevel.HIGH)).thenReturn(1L);
        when(service.profiles(tenantId, null, 0, 200)).thenReturn(List.of(profile));
        when(service.profileCount(tenantId, null)).thenReturn(1L);

        RequestContextHolder.set(new RequestContext(
                TenantId.of(tenantId),
                UUID.randomUUID(),
                "clinic.admin@test",
                Set.of("CLINIC_ADMIN"),
                "CLINIC_ADMIN",
                "corr-engagement-route"
        ));

        mvc.perform(get("/api/carepilot/engagement/overview").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highEngagementCount").value(1))
                .andExpect(jsonPath("$.criticalEngagementCount").value(0))
                .andExpect(jsonPath("$.cohortCounts.HIGH_RISK_PATIENTS").value(1));

        mvc.perform(get("/api/carepilot/engagement/cohorts").param("cohort", "HIGH_RISK_PATIENTS").param("limit", "200").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.rows[0].patientNumber").value("PAT-1001"))
                .andExpect(jsonPath("$.rows[0].engagementLevel").value("HIGH"))
                .andExpect(jsonPath("$.rows[0].suggestedCampaignType").value("APPOINTMENT_REMINDER"));

        mvc.perform(get("/api/carepilot/engagement/profiles").param("level", "HIGH").param("limit", "200").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedLevel").value("HIGH"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.rows[0].patientName").value("Jane Doe"));

        mvc.perform(get("/api/carepilot/engagement/profiles").param("level", "ALL").param("limit", "200").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedLevel").value("ALL"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.rows[0].patientNumber").value("PAT-1001"));
    }
}
