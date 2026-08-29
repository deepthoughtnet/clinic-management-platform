package com.deepthoughtnet.clinic.api.vaccination;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PatientVaccinationControllerSecurityTest {
    @Test
    void verifyExternalHistoryRequiresTenantScopedPlatformAdminOrClinicalRoles() throws Exception {
        Method verifyExternalHistory = PatientVaccinationController.class.getMethod(
                "verifyExternalHistory",
                java.util.UUID.class,
                java.util.UUID.class,
                com.deepthoughtnet.clinic.api.vaccination.dto.PatientVaccinationUpdateRequest.class
        );

        assertThat(verifyExternalHistory.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@vaccineAccessChecker.canVerifyExternalVaccination() or @doctorAssignmentSecurityService.isDoctor()");
    }
}
