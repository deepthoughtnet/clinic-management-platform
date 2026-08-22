package com.deepthoughtnet.clinic.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminIntegrationsControllerSecurityTest {

    @Test
    void statusAllowsPlatformAdminOnly() throws Exception {
        Method method = AdminIntegrationsController.class.getMethod("status");
        String guard = method.getAnnotation(PreAuthorize.class).value();

        assertThat(guard).contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("CLINIC_ADMIN").doesNotContain("AUDITOR").doesNotContain("PLATFORM_TENANT_SUPPORT");
        assertThat(guard).doesNotContain("DOCTOR").doesNotContain("BILLING_USER").doesNotContain("RECEPTIONIST");
    }
}
