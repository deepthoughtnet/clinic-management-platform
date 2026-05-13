package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotAnalyticsOpsControllerSecurityTest {

    @Test
    void analyticsSummaryAllowsAdminAuditorAndPlatformTenantScopedRoles() throws Exception {
        Method summary = CarePilotAnalyticsController.class.getMethod("summary", java.time.LocalDate.class, java.time.LocalDate.class, java.util.UUID.class);
        String guard = summary.getAnnotation(PreAuthorize.class).value();

        assertThat(guard).contains("CLINIC_ADMIN");
        assertThat(guard).contains("AUDITOR");
        assertThat(guard).contains("PLATFORM_ADMIN");
    }

    @Test
    void opsReadsAllowAdminAuditorAndPlatformTenantScopedRoles() throws Exception {
        Method failed = CarePilotOpsController.class.getMethod(
                "failedExecutions",
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                java.util.UUID.class,
                com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType.class,
                com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus.class,
                java.lang.String.class,
                boolean.class
        );
        Method timeline = CarePilotOpsController.class.getMethod("timeline", java.util.UUID.class);

        String failedGuard = failed.getAnnotation(PreAuthorize.class).value();
        String timelineGuard = timeline.getAnnotation(PreAuthorize.class).value();

        assertThat(failedGuard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("PLATFORM_ADMIN");
        assertThat(timelineGuard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("PLATFORM_ADMIN");
    }
}
