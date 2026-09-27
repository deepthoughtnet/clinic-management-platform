package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DotVoiceProviderConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DotVoiceProviderConfig.class)
            .withPropertyValues(
                    "clinic.carepilot.voice.dotvoice.enabled=true",
                    "clinic.carepilot.voice.dotvoice.api-key=runtime-secret",
                    "clinic.carepilot.voice.dotvoice.base-url=http://rec.mindserv.co:8000",
                    "clinic.carepilot.voice.dotvoice.calls-path=/v1/calls",
                    "clinic.carepilot.voice.dotvoice.from-number=7969812868",
                    "clinic.carepilot.voice.dotvoice.trunk=trunk2",
                    "clinic.carepilot.voice.dotvoice.timeout-ms=10000",
                    "clinic.carepilot.voice.dotvoice.stream-enabled=true",
                    "clinic.carepilot.voice.dotvoice.stream-url=ws://stream.test/vendor/stream",
                    "clinic.carepilot.voice.dotvoice.stream-timeout-ms=30000",
                    "clinic.carepilot.voice.dotvoice.media-frame-duration-ms=20",
                    "clinic.carepilot.voice.dotvoice.callback-base-url="
            );

    @Test
    void bindsRuntimeStageOneConfigurationAndDoesNotRequireCallback() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            DotVoiceVoiceCallProvider provider = context.getBean(DotVoiceVoiceCallProvider.class);
            assertThat(provider.isReady()).isTrue();
            DotVoiceProperties properties = context.getBean(DotVoiceProperties.class);
            assertThat(properties.isStreamEnabled()).isTrue();
            assertThat(properties.getMediaFrameDurationMs()).isEqualTo(20);
        });
    }
}
