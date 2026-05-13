package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotEngagementControllerSecurityTest {

    @Test
    void engagementEndpointsAllowAdminAuditorAndPlatformTenantScopedRoles() throws Exception {
        Method overview = CarePilotEngagementController.class.getMethod("overview");
        Method cohorts = CarePilotEngagementController.class.getMethod(
                "cohorts",
                com.deepthoughtnet.clinic.carepilot.engagement.model.EngagementCohortType.class,
                int.class,
                int.class
        );
        Method highRisk = CarePilotEngagementController.class.getMethod("highRisk", int.class, int.class);
        Method inactive = CarePilotEngagementController.class.getMethod("inactive", int.class, int.class);

        assertAllowed(overview.getAnnotation(PreAuthorize.class).value());
        assertAllowed(cohorts.getAnnotation(PreAuthorize.class).value());
        assertAllowed(highRisk.getAnnotation(PreAuthorize.class).value());
        assertAllowed(inactive.getAnnotation(PreAuthorize.class).value());
    }

    private void assertAllowed(String guard) {
        assertThat(guard).contains("CLINIC_ADMIN");
        assertThat(guard).contains("AUDITOR");
        assertThat(guard).contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("DOCTOR").doesNotContain("BILLING_USER").doesNotContain("RECEPTIONIST");
    }
}
