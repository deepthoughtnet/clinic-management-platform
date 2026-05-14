package com.deepthoughtnet.clinic.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminNotificationSettingsControllerSecurityTest {

    @Test
    void getAllowsAdminAuditorReceptionAndPlatformTenantRoles() throws Exception {
        Method method = AdminNotificationSettingsController.class.getMethod("get");
        String guard = method.getAnnotation(PreAuthorize.class).value();

        assertThat(guard).contains("CLINIC_ADMIN").contains("AUDITOR").contains("RECEPTIONIST").contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("DOCTOR").doesNotContain("BILLING_USER");
    }

    @Test
    void updateAllowsOnlyAdminAndPlatformTenantRoles() throws Exception {
        Method method = AdminNotificationSettingsController.class.getMethod(
                "update",
                com.deepthoughtnet.clinic.api.admin.dto.AdminNotificationSettingsDtos.UpdateNotificationSettingsRequest.class
        );
        String guard = method.getAnnotation(PreAuthorize.class).value();

        assertThat(guard).contains("CLINIC_ADMIN").contains("PLATFORM_ADMIN").contains("PLATFORM_TENANT_SUPPORT");
        assertThat(guard).doesNotContain("AUDITOR").doesNotContain("DOCTOR").doesNotContain("BILLING_USER").doesNotContain("RECEPTIONIST");
    }
}
