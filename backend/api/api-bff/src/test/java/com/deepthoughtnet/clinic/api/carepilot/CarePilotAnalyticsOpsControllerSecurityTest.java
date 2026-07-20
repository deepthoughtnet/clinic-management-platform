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

        assertThat(guard).contains("engage.analytics.view");
    }

    @Test
    void opsReadsUseDedicatedOpsPermission() throws Exception {
        Method executions = CarePilotOpsController.class.getMethod(
                "executions",
                String.class,
                com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType.class,
                com.deepthoughtnet.clinic.carepilot.execution.model.ExecutionStatus.class,
                String.class,
                boolean.class,
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                String.class
        );
        Method queuedExecutions = CarePilotOpsController.class.getMethod(
                "queuedExecutions",
                String.class,
                com.deepthoughtnet.clinic.carepilot.messaging.model.ChannelType.class,
                String.class,
                boolean.class,
                java.time.LocalDate.class,
                java.time.LocalDate.class,
                String.class
        );
        Method readiness = CarePilotOpsController.class.getMethod("readiness");
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

        String executionsGuard = executions.getAnnotation(PreAuthorize.class).value();
        String queuedGuard = queuedExecutions.getAnnotation(PreAuthorize.class).value();
        String readinessGuard = readiness.getAnnotation(PreAuthorize.class).value();
        String failedGuard = failed.getAnnotation(PreAuthorize.class).value();
        String timelineGuard = timeline.getAnnotation(PreAuthorize.class).value();

        assertThat(executionsGuard).contains("engage.ops.view");
        assertThat(queuedGuard).contains("engage.ops.view");
        assertThat(readinessGuard).contains("engage.ops.view");
        assertThat(failedGuard).contains("engage.ops.view");
        assertThat(timelineGuard).contains("engage.ops.view");
    }
}
