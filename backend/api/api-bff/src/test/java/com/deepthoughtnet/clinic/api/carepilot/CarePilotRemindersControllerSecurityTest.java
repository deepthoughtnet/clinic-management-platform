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
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                String.class,
                int.class,
                int.class
        );
        Method detail = CarePilotRemindersController.class.getMethod("detail", java.util.UUID.class);

        String listGuard = list.getAnnotation(PreAuthorize.class).value();
        String detailGuard = detail.getAnnotation(PreAuthorize.class).value();

        assertThat(listGuard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("PLATFORM_ADMIN");
        assertThat(detailGuard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("PLATFORM_ADMIN");
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

        assertThat(retryGuard).contains("CLINIC_ADMIN").contains("PLATFORM_ADMIN");
        assertThat(resendGuard).contains("CLINIC_ADMIN").contains("PLATFORM_ADMIN");
        assertThat(cancelGuard).contains("CLINIC_ADMIN").contains("PLATFORM_ADMIN");
        assertThat(suppressGuard).contains("CLINIC_ADMIN").contains("PLATFORM_ADMIN");
        assertThat(rescheduleGuard).contains("CLINIC_ADMIN").contains("PLATFORM_ADMIN");
        assertThat(retryGuard).doesNotContain("AUDITOR").doesNotContain("DOCTOR").doesNotContain("BILLING_USER").doesNotContain("RECEPTIONIST");
    }
}
