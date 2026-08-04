package com.deepthoughtnet.clinic.api.clinic;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

class DiscoverPresenceControllerSecurityTest {
    @Test
    void discoverPresenceControllerUsesExpectedRoutesAndGuards() throws Exception {
        RequestMapping requestMapping = DiscoverPresenceController.class.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).isNotNull();
        assertThat(requestMapping.value()).containsExactly("/api");

        Method clinicPresence = DiscoverPresenceController.class.getMethod("clinicPresence");
        Method createClinicClaimIntent = DiscoverPresenceController.class.getMethod("createClinicClaimIntent");
        Method doctorPresence = DiscoverPresenceController.class.getMethod("doctorPresence", java.util.UUID.class);
        Method createDoctorClaimIntent = DiscoverPresenceController.class.getMethod("createDoctorClaimIntent", java.util.UUID.class);

        assertThat(clinicPresence.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('clinic.read')");
        assertThat(createClinicClaimIntent.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('clinic.update')");
        assertThat(doctorPresence.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('user.read') or @permissionChecker.hasPermission('appointment.manage')");
        assertThat(createDoctorClaimIntent.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('user.read') or @permissionChecker.hasPermission('appointment.manage')");
    }
}
