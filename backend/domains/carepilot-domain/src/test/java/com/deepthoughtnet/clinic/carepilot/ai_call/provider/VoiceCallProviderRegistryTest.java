package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.voice.spi.VoiceCallProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoiceCallProviderRegistryTest {

    @Test
    void configuredDotVoiceWinsOverReadyMockRegardlessOfBeanOrder() {
        VoiceCallProvider mockProvider = provider("mock-voice", true);
        VoiceCallProvider dotVoice = provider("dotvoice", true);

        VoiceCallProviderRegistry registry = new VoiceCallProviderRegistry(
                List.of(mockProvider, dotVoice), "dotvoice", "none", false);

        assertThat(registry.resolvePrimary()).isSameAs(dotVoice);
    }

    @Test
    void mockProviderRemainsAvailableWhenExplicitlySelectedForIsolatedTests() {
        VoiceCallProvider mockProvider = provider("mock-voice", true);
        VoiceCallProvider dotVoice = provider("dotvoice", true);

        VoiceCallProviderRegistry registry = new VoiceCallProviderRegistry(
                List.of(dotVoice, mockProvider), "mock-voice", "none", false);

        assertThat(registry.resolvePrimary()).isSameAs(mockProvider);
    }

    private VoiceCallProvider provider(String name, boolean ready) {
        VoiceCallProvider provider = mock(VoiceCallProvider.class);
        when(provider.providerName()).thenReturn(name);
        when(provider.isReady()).thenReturn(ready);
        return provider;
    }
}
