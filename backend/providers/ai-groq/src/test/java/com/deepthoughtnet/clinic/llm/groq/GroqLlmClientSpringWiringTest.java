package com.deepthoughtnet.clinic.llm.groq;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

class GroqLlmClientSpringWiringTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "clinic.ai.enabled=true",
                    "clinic.ai.groq.enabled=true",
                    "clinic.ai.groq.api-key=test-key",
                    "clinic.ai.groq.base-url=http://localhost:65535",
                    "clinic.ai.groq.model=openai/gpt-oss-20b",
                    "clinic.ai.groq.timeout-seconds=1"
            );

    @Test
    void createsGroqClientBeanWithInjectedConstructor() {
        contextRunner.run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasSingleBean(GroqLlmClient.class);
            assertThat(context.getBean(GroqLlmClient.class).providerName()).isEqualTo("GROQ");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = GroqLlmClient.class)
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
