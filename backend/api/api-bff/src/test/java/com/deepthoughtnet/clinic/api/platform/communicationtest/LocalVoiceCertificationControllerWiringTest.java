package com.deepthoughtnet.clinic.api.platform.communicationtest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LocalVoiceCertificationControllerWiringTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(LocalVoiceCertificationController.class, TestBeans.class);

    @Test
    void harnessIsAbsentByDefault() {
        runner.run(context -> assertFalse(context.containsBean("localVoiceCertificationController")));
    }

    @Test
    void harnessIsAbsentOutsideDockerProfileEvenWhenPropertyIsEnabled() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod", "voice-certification"))
                .withPropertyValues("aiva.voice.certification-harness.enabled=true")
                .run(context -> assertFalse(context.containsBean("localVoiceCertificationController")));
    }

    @Test
    void harnessRequiresBothExplicitLocalProfileAndProperty() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("docker", "voice-certification"))
                .withPropertyValues("aiva.voice.certification-harness.enabled=true")
                .run(context -> assertTrue(context.containsBean("localVoiceCertificationController")));
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        CommunicationTestService communicationTestService() {
            return mock(CommunicationTestService.class);
        }

        @Bean
        VoiceReadOnlyAivaService voiceReadOnlyAivaService() {
            return mock(VoiceReadOnlyAivaService.class);
        }
    }
}
