package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderVoiceRendererTest {
    @Test
    void selectsConfiguredElevenLabsProviderAndAcceptsEnIn() {
        TextToSpeechProvider elevenLabs = provider("elevenlabs", true,
                new VoiceSynthesisResult(new byte[]{1, 2}, "audio/mpeg", "elevenlabs", null));
        VoiceTestProperties properties = properties("elevenlabs", "piper");

        var result = new ReminderVoiceRenderer(List.of(elevenLabs), properties).render("Test reminder", "en-IN");

        assertEquals("elevenlabs", result.provider());
        assertNotNull(result.audio());
        assertEquals("audio/mpeg", result.audio().contentType());
        var request = org.mockito.ArgumentCaptor.forClass(com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest.class);
        verify(elevenLabs).synthesize(request.capture());
        assertEquals("pcm_16000", request.getValue().outputFormat());
    }

    @Test
    void fallsBackWhenPrimarySynthesisFails() {
        TextToSpeechProvider primary = provider("elevenlabs", true, null);
        when(primary.synthesize(any())).thenThrow(new IllegalStateException("provider failure"));
        TextToSpeechProvider fallback = provider("piper", true,
                new VoiceSynthesisResult(new byte[]{3}, "audio/wav", "piper", null));

        var result = new ReminderVoiceRenderer(List.of(primary, fallback), properties("elevenlabs", "piper"))
                .render("Test reminder", "en-IN");

        assertEquals("piper", result.provider());
        assertNotNull(result.audio());
    }

    @Test
    void distinguishesUnavailableAndInvalidAudio() {
        var unavailable = new ReminderVoiceRenderer(List.of(provider("elevenlabs", false, null)), properties("elevenlabs"))
                .render("Test", "en-IN");
        assertEquals("TTS_PROVIDER_UNAVAILABLE", unavailable.failureCategory());

        var invalid = new ReminderVoiceRenderer(List.of(provider("elevenlabs", true,
                new VoiceSynthesisResult(new byte[0], "audio/mpeg", "elevenlabs", null))), properties("elevenlabs"))
                .render("Test", "en-IN");
        assertEquals("TTS_AUDIO_INVALID", invalid.failureCategory());
    }

    private TextToSpeechProvider provider(String name, boolean ready, VoiceSynthesisResult result) {
        TextToSpeechProvider provider = mock(TextToSpeechProvider.class);
        when(provider.providerName()).thenReturn(name);
        when(provider.isReady()).thenReturn(ready);
        when(provider.synthesize(any())).thenReturn(result);
        return provider;
    }

    private VoiceTestProperties properties(String... order) {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getTts().setProviderOrder(List.of(order));
        return properties;
    }
}
