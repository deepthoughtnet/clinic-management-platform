package com.deepthoughtnet.clinic.llm.sarvam;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class SarvamLlmClientSpringWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "clinic.ai.enabled=true",
                    "clinic.ai.sarvam.enabled=true",
                    "clinic.ai.sarvam.api-key=test-key",
                    "clinic.ai.sarvam.base-url=http://localhost:65535",
                    "clinic.ai.sarvam.model=sarvam-105b-conversations",
                    "clinic.ai.sarvam.timeout-seconds=1"
            );

    @Test
    void createsSarvamClientBeanWithInjectedConstructor() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(SarvamLlmClient.class);
            assertThat(context.getBean(SarvamLlmClient.class).providerName()).isEqualTo("SARVAM");
            assertThat(context.getBean(SarvamLlmClient.class).isAvailable()).isTrue();
        });
    }

    @Test
    void doesNotCreateSarvamClientWhenSemanticProviderIsDisabled() {
        contextRunner.withPropertyValues("clinic.ai.sarvam.enabled=false").run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).doesNotHaveBean(SarvamLlmClient.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = SarvamLlmClient.class)
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
