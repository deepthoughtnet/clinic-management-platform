package com.deepthoughtnet.clinic.api.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AiDoctorCopilotControllerSecurityTest {
    @Test
    void clinicalContextEndpointIsReadScoped() throws Exception {
        Method method = AiDoctorCopilotController.class.getMethod("clinicalContext", java.util.UUID.class, java.util.UUID.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .contains("consultation.read")
                .contains("patient.read")
                .contains("ai_copilot.read");
    }
}
