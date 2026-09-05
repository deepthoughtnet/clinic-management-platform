package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.PropertySource;

class VoiceTestPropertiesBindingTest {

    @Test
    void applicationYamlBindsVoiceProperties() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(source -> environment.getPropertySources().addLast(source));

        Binder binder = Binder.get(environment);
        VoiceTestProperties properties = binder.bind("voice", Bindable.of(VoiceTestProperties.class))
                .orElseThrow(() -> new AssertionError("voice properties did not bind"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getStt().getProviderOrder()).containsExactly("sarvam", "faster-whisper", "mock");
        assertThat(properties.getTts().getProviderOrder()).containsExactly("elevenlabs", "sarvam", "piper", "mock");
        assertThat(properties.getTts().getElevenlabs().getApiKey()).isNotNull();
        assertThat(properties.getTts().getElevenlabs().getVoiceId()).isNotNull();
        assertThat(properties.getTts().getElevenlabs().getModel()).isNotBlank();
        assertThat(properties.getTts().getElevenlabs().getBaseUrl()).contains("elevenlabs");
        assertThat(properties.getTts().getPiper().getBaseUrl()).contains("piper");
        assertThat(properties.getSarvam().getBaseUrl()).contains("sarvam");
    }
}
