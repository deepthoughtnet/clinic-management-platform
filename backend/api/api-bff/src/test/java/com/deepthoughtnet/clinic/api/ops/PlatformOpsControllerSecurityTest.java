package com.deepthoughtnet.clinic.api.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PlatformOpsControllerSecurityTest {
    @Test
    void alertActionsAllowOnlyPlatformAndClinicAdmins() throws Exception {
        Method acknowledge = PlatformOpsController.class.getMethod("acknowledge", java.util.UUID.class);
        String guard = acknowledge.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("PLATFORM_ADMIN").contains("CLINIC_ADMIN");
        assertThat(guard).doesNotContain("AUDITOR");
    }

    @Test
    void alertReadAllowsAuditor() throws Exception {
        Method alerts = PlatformOpsController.class.getMethod("alerts");
        String guard = alerts.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("PLATFORM_ADMIN").contains("CLINIC_ADMIN").contains("AUDITOR");
    }
}
