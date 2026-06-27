package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

class AivaResponseComposerPropertiesBindingTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsAivaResponseComposerPropertiesFromConfiguration() {
        contextRunner
                .withPropertyValues(
                        "aiva.response-composer.enabled=true",
                        "aiva.response-composer.timeout-seconds=7",
                        "aiva.response-composer.voice-only=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(AivaResponseComposerProperties.class);
                    AivaResponseComposerProperties properties = context.getBean(AivaResponseComposerProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getTimeoutSeconds()).isEqualTo(7);
                    assertThat(properties.isVoiceOnly()).isFalse();
                });
    }

    @Configuration
    @EnableConfigurationProperties(AivaResponseComposerProperties.class)
    static class TestConfig {
    }
}
