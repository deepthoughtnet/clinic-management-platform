package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotRemindersControllerSecurityTest {

    @Test
    void readEndpointsAllowAdminAuditorAndPlatformTenantRoles() throws Exception {
        Method list = CarePilotRemindersController.class.getMethod(
                "list",
                String.class,
                java.util.UUID.class,
                com.deepthoughtnet.clinic.carepilot.campaign.model.CampaignType.class,
                com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType.class,
                java.util.UUID.class,
                String.class,
                String.class,
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                String.class,
                int.class,
                int.class
        );
        Method detail = CarePilotRemindersController.class.getMethod("detail", java.util.UUID.class);

        String listGuard = list.getAnnotation(PreAuthorize.class).value();
        String detailGuard = detail.getAnnotation(PreAuthorize.class).value();

        assertThat(listGuard).contains("engage.reminder.view").contains("engage.reminder.operate").contains("engage.audit.view");
        assertThat(detailGuard).contains("engage.reminder.view").contains("engage.reminder.operate").contains("engage.audit.view");
    }

    @Test
    void mutationEndpointsExcludeAuditorAndDoctorRoles() throws Exception {
        Method retry = CarePilotRemindersController.class.getMethod("retry", java.util.UUID.class);
        Method resend = CarePilotRemindersController.class.getMethod("resend", java.util.UUID.class);
        Method cancel = CarePilotRemindersController.class.getMethod("cancel", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderMutationRequest.class);
        Method suppress = CarePilotRemindersController.class.getMethod("suppress", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderMutationRequest.class);
        Method reschedule = CarePilotRemindersController.class.getMethod("reschedule", java.util.UUID.class, com.deepthoughtnet.clinic.api.carepilot.dto.RemindersDtos.ReminderMutationRequest.class);

        String retryGuard = retry.getAnnotation(PreAuthorize.class).value();
        String resendGuard = resend.getAnnotation(PreAuthorize.class).value();
        String cancelGuard = cancel.getAnnotation(PreAuthorize.class).value();
        String suppressGuard = suppress.getAnnotation(PreAuthorize.class).value();
        String rescheduleGuard = reschedule.getAnnotation(PreAuthorize.class).value();

        assertThat(retryGuard).contains("engage.reminder.operate");
        assertThat(resendGuard).contains("engage.reminder.operate");
        assertThat(cancelGuard).contains("engage.reminder.operate");
        assertThat(suppressGuard).contains("engage.reminder.operate");
        assertThat(rescheduleGuard).contains("engage.reminder.operate");
        assertThat(retryGuard).doesNotContain("engage.audit.view").doesNotContain("engage.analytics.view").doesNotContain("engage.provider.admin");
    }
}
