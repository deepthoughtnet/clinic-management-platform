package com.deepthoughtnet.clinic.api.vaccination;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class VaccinationControllerSecurityTest {
    @Test
    void vaccinationEndpointsShareTheExpectedReadGuard() throws Exception {
        Method due = VaccinationController.class.getMethod("due");
        Method overdue = VaccinationController.class.getMethod("overdue");
        Method history = VaccinationController.class.getMethod("history", java.util.UUID.class, java.util.UUID.class, java.time.LocalDate.class, java.time.LocalDate.class, String.class);
        Method recommendations = VaccinationController.class.getMethod("recommendations", java.util.UUID.class, String.class);

        assertThat(due.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')");
        assertThat(overdue.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')");
        assertThat(history.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')");
        assertThat(recommendations.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('vaccination.manage') or @permissionChecker.hasPermission('patient.read')");
    }
}
