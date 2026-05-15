package com.deepthoughtnet.clinic.api.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class RealtimeVoiceControllerSecurityTest {
    @Test
    void turnAllowsReceptionistButNotAuditor() throws Exception {
        Method method = RealtimeVoiceController.class.getMethod("processTurn", java.util.UUID.class,
                com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceTurnRequest.class);
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("RECEPTIONIST");
        assertThat(guard).doesNotContain("AUDITOR");
    }

    @Test
    void summaryAllowsAuditor() throws Exception {
        Method method = RealtimeVoiceController.class.getMethod("summary");
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("AUDITOR").contains("CLINIC_ADMIN").contains("PLATFORM_ADMIN");
    }

    @Test
    void receptionistTestMessageAllowsReceptionistButNotAuditor() throws Exception {
        Method method = RealtimeVoiceController.class.getMethod("receptionistTestMessage",
                com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.ReceptionistTestMessageRequest.class);
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("RECEPTIONIST");
        assertThat(guard).doesNotContain("AUDITOR");
    }
}
