package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotCampaignControllerSecurityTest {

    @Test
    void runtimeAllowsAdminAuditorAndTenantScopedPlatformRoles() throws Exception {
        Method runtime = CarePilotCampaignController.class.getMethod("runtime", java.util.UUID.class);
        String guard = runtime.getAnnotation(PreAuthorize.class).value();

        assertThat(guard).contains("engage.campaign.view");
        assertThat(guard).contains("engage.audit.view");
    }

    @Test
    void createSubmitApproveAndActivateUseTheExpectedCentralPermissions() throws Exception {
        Method create = CarePilotCampaignController.class.getMethod("create", com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CreateCampaignRequest.class);
        Method submit = CarePilotCampaignController.class.getMethod("submit", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignReviewRequest.class);
        Method approve = CarePilotCampaignController.class.getMethod("approve", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.CampaignDtos.CampaignReviewRequest.class);
        Method activate = CarePilotCampaignController.class.getMethod("activate", java.util.UUID.class);
        Method deactivate = CarePilotCampaignController.class.getMethod("deactivate", java.util.UUID.class);
        Method resume = CarePilotCampaignController.class.getMethod("resume", java.util.UUID.class);
        Method trigger = CarePilotCampaignController.class.getMethod("trigger", java.util.UUID.class);

        assertThat(create.getAnnotation(PreAuthorize.class).value()).contains("engage.campaign.manage");
        assertThat(submit.getAnnotation(PreAuthorize.class).value()).contains("engage.campaign.submit");
        assertThat(approve.getAnnotation(PreAuthorize.class).value()).contains("engage.campaign.approve");
        assertThat(activate.getAnnotation(PreAuthorize.class).value()).contains("engage.campaign.activate");
        assertThat(deactivate.getAnnotation(PreAuthorize.class).value()).contains("engage.campaign.activate");
        assertThat(resume.getAnnotation(PreAuthorize.class).value()).contains("engage.campaign.activate");
        assertThat(trigger.getAnnotation(PreAuthorize.class).value()).contains("engage.campaign.activate");
        assertThat(trigger.getAnnotation(PreAuthorize.class).value()).doesNotContain("engage.audit.view");
    }
}
