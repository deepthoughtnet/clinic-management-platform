package com.deepthoughtnet.clinic.api.patientportal;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.api.patient.PatientController;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PatientPortalControllerSecurityTest {

    @Test
    void patientPortalEndpointsRequirePatientRole() {
        PreAuthorize preAuthorize = PatientPortalController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("@permissionChecker.hasRole('PATIENT')");
    }

    @Test
    void existingStaffPatientControllerSecurityRemainsPermissionBased() throws Exception {
        Method getPatient = PatientController.class.getMethod("get", UUID.class);

        assertThat(getPatient.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(getPatient.getAnnotation(PreAuthorize.class).value()).isEqualTo("@permissionChecker.hasPermission('patient.read')");
    }
}
