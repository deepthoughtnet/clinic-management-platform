package com.deepthoughtnet.clinic.api.carepilot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deepthoughtnet.clinic.api.security.PermissionChecker;
import com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType;
import com.deepthoughtnet.clinic.carepilot.template.service.CampaignTemplateService;
import com.deepthoughtnet.clinic.carepilot.template.service.model.CampaignTemplateRecord;
import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import com.deepthoughtnet.clinic.platform.core.context.TenantId;
import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CarePilotTemplateController.class)
@Import({
        PermissionChecker.class,
        CarePilotTemplateControllerAuthorizationTest.MethodSecurityConfig.class,
        CarePilotTemplateControllerAuthorizationTest.ControllerConfig.class
})
class CarePilotTemplateControllerAuthorizationTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockBean private CampaignTemplateService templateService;

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "CLINIC_ADMIN")
    void clinicAdminCanListTemplatesForReviewButCannotMutate() throws Exception {
        setRequestContext("CLINIC_ADMIN");
        when(templateService.list(TENANT_ID)).thenReturn(List.of(sampleTemplate()));

        mockMvc.perform(get("/api/carepilot/templates"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/templates")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Reminder",
                                  "channelType":"EMAIL",
                                  "subjectLine":"Hello",
                                  "bodyTemplate":"Body",
                                  "active":true
                                }
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/carepilot/templates/{templateId}", TEMPLATE_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Reminder",
                                  "subjectLine":"Hello",
                                  "bodyTemplate":"Body",
                                  "active":true
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(templateService).list(TENANT_ID);
        verify(templateService, never()).create(any(), any());
        verify(templateService, never()).patch(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_MANAGER")
    void engageManagerKeepsTemplateManageAccess() throws Exception {
        setRequestContext("ENGAGE_MANAGER");
        when(templateService.list(TENANT_ID)).thenReturn(List.of(sampleTemplate()));
        when(templateService.create(eq(TENANT_ID), any())).thenReturn(sampleTemplate());
        when(templateService.patch(eq(TENANT_ID), eq(TEMPLATE_ID), any())).thenReturn(sampleTemplate());

        mockMvc.perform(get("/api/carepilot/templates"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/carepilot/templates")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Reminder",
                                  "channelType":"EMAIL",
                                  "subjectLine":"Hello",
                                  "bodyTemplate":"Body",
                                  "active":true
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/api/carepilot/templates/{templateId}", TEMPLATE_ID)
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Reminder",
                                  "subjectLine":"Hello",
                                  "bodyTemplate":"Body",
                                  "active":true
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ENGAGE_EXECUTIVE")
    void engageExecutiveCannotAccessTemplateManagement() throws Exception {
        setRequestContext("ENGAGE_EXECUTIVE");

        mockMvc.perform(get("/api/carepilot/templates"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/carepilot/templates")
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Reminder",
                                  "channelType":"EMAIL",
                                  "subjectLine":"Hello",
                                  "bodyTemplate":"Body",
                                  "active":true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private void setRequestContext(String role) {
        RequestContextHolder.set(new RequestContext(
                TenantId.of(TENANT_ID),
                UUID.randomUUID(),
                "tester@example.com",
                Set.of(role),
                role,
                "corr-template-auth"
        ));
    }

    private CampaignTemplateRecord sampleTemplate() {
        return new CampaignTemplateRecord(
                TEMPLATE_ID,
                TENANT_ID,
                "Reminder",
                ChannelType.EMAIL,
                "Hello",
                "Body",
                true,
                OffsetDateTime.parse("2026-07-18T00:00:00Z"),
                OffsetDateTime.parse("2026-07-18T00:00:00Z")
        );
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @Configuration
    static class ControllerConfig {
        @Bean
        CarePilotTemplateController controller(CampaignTemplateService templateService) {
            return new CarePilotTemplateController(templateService);
        }
    }
}
