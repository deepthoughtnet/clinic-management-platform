package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SecurityConfigWebSocketPathTest {

    private static final Path SECURITY_CONFIG_PATH = Path.of(
            "src/main/java/com/deepthoughtnet/clinic/api/config/SecurityConfig.java"
    );

    @Test
    void patientVoiceWebSocketPathIsExplicitlyPermitted() throws IOException {
        String source = Files.readString(SECURITY_CONFIG_PATH);

        assertThat(source).contains(".requestMatchers(\"/ws/patient-portal/careai\").permitAll()");
    }

    @Test
    void adminVoiceWebSocketPathRemainsPermitted() throws IOException {
        String source = Files.readString(SECURITY_CONFIG_PATH);

        assertThat(source).contains(".requestMatchers(\"/ws/voice/**\").permitAll()");
    }

    @Test
    void wildcardWsPermitIsNotIntroduced() throws IOException {
        String source = Files.readString(SECURITY_CONFIG_PATH);

        assertThat(source).doesNotContain(".requestMatchers(\"/ws/**\").permitAll()");
    }
}
