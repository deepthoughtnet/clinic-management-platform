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

        assertThat(guard).contains("CLINIC_ADMIN");
        assertThat(guard).contains("AUDITOR");
        assertThat(guard).contains("PLATFORM_ADMIN");
    }
}
