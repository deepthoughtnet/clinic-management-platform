package com.deepthoughtnet.clinic.api.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PlatformTenantControllerSecurityTest {

    @Test
    void platformTenantControllerRequiresPlatformAdminOnly() {
        PreAuthorize preAuthorize = PlatformTenantController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@permissionChecker.hasRole('PLATFORM_ADMIN')");
    }
}
