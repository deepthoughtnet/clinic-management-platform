package com.deepthoughtnet.clinic.api.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class RealtimeVoiceControllerSecurityTest {
    @Test
    void turnAllowsPlatformAdminsOnly() throws Exception {
        Method method = RealtimeVoiceController.class.getMethod("processTurn", java.util.UUID.class,
                com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.VoiceTurnRequest.class);
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("CLINIC_ADMIN").doesNotContain("RECEPTIONIST").doesNotContain("AUDITOR");
    }

    @Test
    void summaryAllowsPlatformAdminsOnly() throws Exception {
        Method method = RealtimeVoiceController.class.getMethod("summary");
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("CLINIC_ADMIN").doesNotContain("RECEPTIONIST").doesNotContain("AUDITOR");
    }

    @Test
    void receptionistTestMessageAllowsPlatformAdminsOnly() throws Exception {
        Method method = RealtimeVoiceController.class.getMethod("receptionistTestMessage",
                com.deepthoughtnet.clinic.api.realtime.dto.RealtimeVoiceDtos.ReceptionistTestMessageRequest.class);
        String guard = method.getAnnotation(PreAuthorize.class).value();
        assertThat(guard).contains("PLATFORM_ADMIN");
        assertThat(guard).doesNotContain("CLINIC_ADMIN").doesNotContain("RECEPTIONIST").doesNotContain("AUDITOR");
    }
}
