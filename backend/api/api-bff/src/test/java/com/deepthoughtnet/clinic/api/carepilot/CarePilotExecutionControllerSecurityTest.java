package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotExecutionControllerSecurityTest {

    @Test
    void retryAndResendAllowClinicAdminAndTenantScopedPlatformSupport() throws Exception {
        Method retry = CarePilotExecutionController.class.getMethod("retry", java.util.UUID.class);
        Method resend = CarePilotExecutionController.class.getMethod("resend", java.util.UUID.class);

        assertThat(retry.getAnnotation(PreAuthorize.class).value()).contains("CLINIC_ADMIN");
        assertThat(retry.getAnnotation(PreAuthorize.class).value()).contains("PLATFORM_TENANT_SUPPORT");
        assertThat(resend.getAnnotation(PreAuthorize.class).value()).contains("CLINIC_ADMIN");
        assertThat(resend.getAnnotation(PreAuthorize.class).value()).contains("PLATFORM_TENANT_SUPPORT");
    }

    @Test
    void failedAndAttemptsSupportAuditorAndTenantScopedPlatformSupport() throws Exception {
        Method failed = CarePilotExecutionController.class.getMethod("listFailed");
        Method attempts = CarePilotExecutionController.class.getMethod("listAttempts", java.util.UUID.class);

        assertThat(failed.getAnnotation(PreAuthorize.class).value()).contains("AUDITOR");
        assertThat(failed.getAnnotation(PreAuthorize.class).value()).contains("PLATFORM_TENANT_SUPPORT");
        assertThat(attempts.getAnnotation(PreAuthorize.class).value()).contains("AUDITOR");
        assertThat(attempts.getAnnotation(PreAuthorize.class).value()).contains("PLATFORM_TENANT_SUPPORT");
    }
}
