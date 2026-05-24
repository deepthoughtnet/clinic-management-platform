package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

class VoiceTestControllerSecurityTest {

    @Test
    void testEndpointRequiresAiVoicePermission() throws Exception {
        Method method = VoiceTestController.class.getMethod("test", MultipartFile.class, String.class, String.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('ai.voice.test')");
    }

    @Test
    void statusEndpointRequiresAiVoicePermission() throws Exception {
        Method method = VoiceTestController.class.getMethod("status", boolean.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('ai.voice.test')");
    }

    @Test
    void debugSttEndpointRequiresAiVoicePermission() throws Exception {
        Method method = VoiceTestController.class.getMethod("debugStt", MultipartFile.class, String.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@permissionChecker.hasPermission('ai.voice.test')");
    }
}
