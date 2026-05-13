package com.deepthoughtnet.clinic.api.carepilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.messaging.spi.MessageChannel;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CarePilotMessagingControllerSecurityTest {

    @Test
    void providerStatusAllowsAdminAuditorAndTenantScopedPlatformRoles() throws Exception {
        Method status = CarePilotMessagingController.class.getMethod("status");
        String guard = status.getAnnotation(PreAuthorize.class).value();

        assertThat(guard).contains("CLINIC_ADMIN");
        assertThat(guard).contains("AUDITOR");
        assertThat(guard).contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("DOCTOR").doesNotContain("BILLING_USER").doesNotContain("RECEPTIONIST");
    }

    @Test
    void testSendAllowsOnlyAdminAndTenantScopedPlatformRoles() throws Exception {
        Method testSend = CarePilotMessagingController.class.getMethod(
                "testSend",
                MessageChannel.class,
                com.deepthoughtnet.clinic.api.carepilot.dto.MessagingDtos.ProviderTestSendRequest.class
        );
        String guard = testSend.getAnnotation(PreAuthorize.class).value();

        assertThat(guard).contains("CLINIC_ADMIN");
        assertThat(guard).contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("AUDITOR").doesNotContain("DOCTOR").doesNotContain("BILLING_USER").doesNotContain("RECEPTIONIST");
    }
}
