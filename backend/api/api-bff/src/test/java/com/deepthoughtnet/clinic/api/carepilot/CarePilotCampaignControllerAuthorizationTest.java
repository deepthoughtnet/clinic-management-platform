package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.carepilot.campaign.model.AudienceType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignStatus;
import com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType;
import com.deepthoughtnet.clinic.carepilot.campaign.model.TriggerType;
import com.deepthoughtnet.clinic.carepilot.campaign.service.CampaignService;
import com.deepthoughtnet.clinic.carepilot.campaign.service.model.CampaignRecord;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.api.reliability.service.IdempotencyService;
import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CarePilotCampaignController.class)
@Import({
        PermissionChecker.class,
        CarePilotCampaignControllerAuthorizationTest.MethodSecurityConfig.class,
        CarePilotCampaignControllerAuthorizationTest.ControllerConfig.class
})
class CarePilotCampaignControllerAuthorizationTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CAMPAIGN_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockBean private CampaignService campaignService;
    @MockBean private CarePilotCampaignRuntimeService runtimeService;
    @MockBean private CarePilotCampaignTriggerService triggerService;
    @MockBean private IdempotencyService idempotencyService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerGetsForbiddenForActivate() throws Exception {
        setRequestContext("ENGAGE_MANAGER");

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/activate", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).activate(any(), any());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_EXECUTIVE")
    void engageExecutiveGetsForbiddenForActivate() throws Exception {
        setRequestContext("ENGAGE_EXECUTIVE");

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/activate", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).activate(any(), any());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void receptionistGetsForbiddenForActivate() throws Exception {
        setRequestContext("RECEPTIONIST");

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/activate", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).activate(any(), any());
    }

    @Test
    @WithMockUser(roles = "AUDITOR")
    void auditorGetsForbiddenForActivate() throws Exception {
        setRequestContext("AUDITOR");

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/activate", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).activate(any(), any());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerGetsForbiddenForDeactivateAndTrigger() throws Exception {
        setRequestContext("ENGAGE_MANAGER");

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/deactivate", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/trigger", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).deactivate(any(), any());
        verify(triggerService, never()).trigger(any(), any());
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCanLoadTriggerPreview() throws Exception {
        setRequestContext("CLINIC_ADMIN");
        when(triggerService.preview(TENANT_ID, CAMPAIGN_ID)).thenReturn(
                new CarePilotCampaignTriggerService.CampaignTriggerPreviewResult(
                        "CAM-2026-000001",
                        "Test Campaign",
                        CampaignStatus.ACTIVE,
                        TriggerType.MANUAL,
                        "EMAIL",
                        "Reminder Template",
                        true,
                        "mock-email",
                        "Mock/Test",
                        true,
                        true,
                        12,
                        1,
                        0,
                        1,
                        0,
                        0,
                        0,
                        0,
                        12,
                        null,
                        "DEMO/UAT environment",
                        true,
                        true,
                        java.util.List.of()
                )
        );

        mockMvc.perform(get("/api/carepilot/campaigns/{campaignId}/trigger-preview", CAMPAIGN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.campaignReference").value("CAM-2026-000001"))
                .andExpect(jsonPath("$.eligibleRecipients").value(12))
                .andExpect(jsonPath("$.canTrigger").value(true));

        verify(triggerService).preview(TENANT_ID, CAMPAIGN_ID);
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void triggerEndpointDoesNotInvokeActivate() throws Exception {
        setRequestContext("CLINIC_ADMIN");
        when(triggerService.trigger(TENANT_ID, CAMPAIGN_ID)).thenReturn(
                new CarePilotCampaignTriggerService.CampaignTriggerResult(
                        "CAM-2026-000001",
                        "EXE-CAM-2026-000001-20260718T000000Z",
                        "Test Campaign",
                        AudienceType.ALL_PATIENTS,
                        ChannelType.EMAIL,
                        CampaignStatus.ACTIVE,
                        true,
                        12,
                        12,
                        0,
                        "Queued",
                        OffsetDateTime.parse("2026-07-18T00:00:00Z")
                )
        );

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/trigger", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}")
                        .header("Idempotency-Key", "trigger-key-1"))
                .andExpect(status().isOk());

        verify(campaignService, never()).activate(any(), any(), any(), any());
        verify(triggerService).trigger(TENANT_ID, CAMPAIGN_ID);
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCanActivateDeactivateResumeAndTrigger() throws Exception {
        setRequestContext("CLINIC_ADMIN");
        when(campaignService.activate(TENANT_ID, CAMPAIGN_ID, ACTOR_ID, "CLINIC_ADMIN")).thenReturn(sampleCampaign(true));
        when(campaignService.deactivate(TENANT_ID, CAMPAIGN_ID, ACTOR_ID, "CLINIC_ADMIN")).thenReturn(sampleCampaign(false));
        when(campaignService.resume(TENANT_ID, CAMPAIGN_ID, ACTOR_ID, "CLINIC_ADMIN")).thenReturn(sampleCampaign(true));
        when(triggerService.trigger(TENANT_ID, CAMPAIGN_ID)).thenReturn(
                new CarePilotCampaignTriggerService.CampaignTriggerResult(
                        "CAM-2026-000001",
                        "EXE-CAM-2026-000001-20260718T000000Z",
                        "Test Campaign",
                        AudienceType.ALL_PATIENTS,
                        ChannelType.EMAIL,
                        CampaignStatus.ACTIVE,
                        true,
                        12,
                        12,
                        0,
                        "Queued",
                        OffsetDateTime.parse("2026-07-18T00:00:00Z")
                )
        );

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/activate", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/deactivate", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}/resume", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/trigger", CAMPAIGN_ID).with(csrf()))
                .andExpect(status().isOk());

        verify(campaignService).activate(TENANT_ID, CAMPAIGN_ID, ACTOR_ID, "CLINIC_ADMIN");
        verify(campaignService).deactivate(TENANT_ID, CAMPAIGN_ID, ACTOR_ID, "CLINIC_ADMIN");
        verify(campaignService).resume(TENANT_ID, CAMPAIGN_ID, ACTOR_ID, "CLINIC_ADMIN");
        verify(triggerService).trigger(TENANT_ID, CAMPAIGN_ID);
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerCanCreateCampaigns() throws Exception {
        setRequestContext("ENGAGE_MANAGER");
        when(campaignService.create(eq(TENANT_ID), any(), eq(ACTOR_ID), eq("ENGAGE_MANAGER"))).thenReturn(sampleCampaign(false));

        mockMvc.perform(post("/api/carepilot/campaigns")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Spring Recall",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "quarterly reminder"
                                }
                                """.formatted(CAMPAIGN_ID)))
                .andExpect(status().isCreated());

        verify(campaignService).create(eq(TENANT_ID), any(), eq(ACTOR_ID), eq("ENGAGE_MANAGER"));
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerCanSubmitCampaigns() throws Exception {
        setRequestContext("ENGAGE_MANAGER");
        when(campaignService.submitForApproval(eq(TENANT_ID), eq(CAMPAIGN_ID), eq(ACTOR_ID), eq("ENGAGE_MANAGER"), eq(0), eq(null))).thenReturn(sampleCampaign(false));

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/submit", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        verify(campaignService).submitForApproval(eq(TENANT_ID), eq(CAMPAIGN_ID), eq(ACTOR_ID), eq("ENGAGE_MANAGER"), eq(0), eq(null));
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerCanPatchCampaigns() throws Exception {
        setRequestContext("ENGAGE_MANAGER");
        when(campaignService.update(eq(TENANT_ID), eq(CAMPAIGN_ID), any(), eq(ACTOR_ID), eq("ENGAGE_MANAGER"))).thenReturn(sampleCampaign(false));

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Spring Recall",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "quarterly reminder",
                                  "expectedVersion": 0
                                }
                                """.formatted(CAMPAIGN_ID)))
                .andExpect(status().isOk());

        verify(campaignService).update(eq(TENANT_ID), eq(CAMPAIGN_ID), any(), eq(ACTOR_ID), eq("ENGAGE_MANAGER"));
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerCanEditAndResubmitCampaigns() throws Exception {
        setRequestContext("ENGAGE_MANAGER");
        when(campaignService.editAndResubmit(eq(TENANT_ID), eq(CAMPAIGN_ID), any(), eq(ACTOR_ID), eq("ENGAGE_MANAGER"), eq(0), eq("Needs more detail")))
                .thenReturn(sampleCampaign(false));

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/edit-and-resubmit", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Spring Recall",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "quarterly reminder",
                                  "expectedVersion": 0,
                                  "resolutionNote": "Needs more detail"
                                }
                                """.formatted(CAMPAIGN_ID)))
                .andExpect(status().isOk());

        verify(campaignService).editAndResubmit(eq(TENANT_ID), eq(CAMPAIGN_ID), any(), eq(ACTOR_ID), eq("ENGAGE_MANAGER"), eq(0), eq("Needs more detail"));
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCanApproveAndRequestChanges() throws Exception {
        setRequestContext("CLINIC_ADMIN");
        when(campaignService.approve(eq(TENANT_ID), eq(CAMPAIGN_ID), eq(ACTOR_ID), eq("CLINIC_ADMIN"), eq("Looks good"), eq(0))).thenReturn(sampleCampaign(false));
        when(campaignService.requestChanges(eq(TENANT_ID), eq(CAMPAIGN_ID), eq(ACTOR_ID), eq("CLINIC_ADMIN"), eq("Please fix"), eq(0))).thenReturn(sampleCampaign(false));

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/approve", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"comment\":\"Looks good\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/request-changes", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"comment\":\"Please fix\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        verify(campaignService).approve(eq(TENANT_ID), eq(CAMPAIGN_ID), eq(ACTOR_ID), eq("CLINIC_ADMIN"), eq("Looks good"), eq(0));
        verify(campaignService).requestChanges(eq(TENANT_ID), eq(CAMPAIGN_ID), eq(ACTOR_ID), eq("CLINIC_ADMIN"), eq("Please fix"), eq(0));
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCannotCreateCampaigns() throws Exception {
        setRequestContext("CLINIC_ADMIN");

        mockMvc.perform(post("/api/carepilot/campaigns")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Spring Recall",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "quarterly reminder"
                                }
                                """.formatted(CAMPAIGN_ID)))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).create(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCannotPatchCampaigns() throws Exception {
        setRequestContext("CLINIC_ADMIN");

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Spring Recall",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "quarterly reminder",
                                  "expectedVersion": 0
                                }
                                """.formatted(CAMPAIGN_ID)))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCannotEditAndResubmitCampaigns() throws Exception {
        setRequestContext("CLINIC_ADMIN");

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/edit-and-resubmit", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Spring Recall",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "quarterly reminder",
                                  "expectedVersion": 0,
                                  "resolutionNote": "Needs more detail"
                                }
                                """.formatted(CAMPAIGN_ID)))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).editAndResubmit(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_EXECUTIVE")
    void engageExecutiveCannotPatchCampaigns() throws Exception {
        setRequestContext("ENGAGE_EXECUTIVE");

        mockMvc.perform(patch("/api/carepilot/campaigns/{campaignId}", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Spring Recall",
                                  "campaignType": "CUSTOM",
                                  "triggerType": "MANUAL",
                                  "audienceType": "ALL_PATIENTS",
                                  "templateId": "%s",
                                  "notes": "quarterly reminder",
                                  "expectedVersion": 0
                                }
                                """.formatted(CAMPAIGN_ID)))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCannotSubmitOrWithdrawCampaigns() throws Exception {
        setRequestContext("CLINIC_ADMIN");

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/submit", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/withdraw", CAMPAIGN_ID)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).submitForApproval(any(), any(), any(), any(), any(), any());
        verify(campaignService, never()).withdrawSubmission(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerCannotApproveOrRequestChanges() throws Exception {
        setRequestContext("ENGAGE_MANAGER");

        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/approve", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"comment\":\"Looks good\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/carepilot/campaigns/{campaignId}/request-changes", CAMPAIGN_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"comment\":\"Please fix\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());

        verify(campaignService, never()).approve(any(), any(), any(), any(), any(), any());
        verify(campaignService, never()).requestChanges(any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = { "ENGAGE_EXECUTIVE", "RECEPTIONIST", "AUDITOR" })
    void otherRolesCannotCreateCampaigns() throws Exception {
        for (String role : Set.of("ENGAGE_EXECUTIVE", "RECEPTIONIST", "AUDITOR")) {
            setRequestContext(role);

            mockMvc.perform(post("/api/carepilot/campaigns")
                            .with(csrf())
                            .contentType("application/json")
                            .content("""
                                    {
                                      "name": "Spring Recall",
                                      "campaignType": "CUSTOM",
                                      "triggerType": "MANUAL",
                                      "audienceType": "ALL_PATIENTS",
                                      "templateId": "%s",
                                      "notes": "quarterly reminder"
                                    }
                                    """.formatted(CAMPAIGN_ID)))
                    .andExpect(status().isForbidden());
        }

        verify(campaignService, never()).create(any(), any(), any(), any());
    }

    @Test
    void activateDeactivateAndTriggerRequireClinicAdminAnnotation() throws Exception {
        assertThat(CarePilotCampaignController.class.getMethod("activate", UUID.class)
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())
                .contains("engage.campaign.activate");
        assertThat(CarePilotCampaignController.class.getMethod("deactivate", UUID.class)
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())
                .contains("engage.campaign.activate");
        assertThat(CarePilotCampaignController.class.getMethod("trigger", UUID.class, String.class, Map.class)
                .getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value())
                .contains("engage.campaign.activate");
    }

    private void setRequestContext(String role) {
        RequestContextHolder.set(new RequestContext(
                TenantId.of(TENANT_ID),
                ACTOR_ID,
                "tester@example.com",
                Set.of(role),
                role,
                "corr-campaign-auth"
        ));
    }

    private CampaignRecord sampleCampaign(boolean active) {
        return new CampaignRecord(
                CAMPAIGN_ID,
                "CAM-2026-000001",
                TENANT_ID,
                "Test Campaign",
                CampaignType.CUSTOM,
                active ? CampaignStatus.ACTIVE : CampaignStatus.PAUSED,
                TriggerType.MANUAL,
                AudienceType.ALL_PATIENTS,
                null,
                active,
                null,
                ACTOR_ID,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                OffsetDateTime.parse("2026-07-18T00:00:00Z"),
                OffsetDateTime.parse("2026-07-18T00:00:00Z")
        );
    }

    @Configuration
    static class ControllerConfig {
        @Bean
        CarePilotCampaignController carePilotCampaignController(
                CampaignService campaignService,
                CarePilotCampaignRuntimeService runtimeService,
                CarePilotCampaignTriggerService triggerService,
                IdempotencyService idempotencyService,
                ObjectMapper objectMapper
        ) {
            return new CarePilotCampaignController(campaignService, runtimeService, triggerService, idempotencyService, objectMapper);
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }
}
