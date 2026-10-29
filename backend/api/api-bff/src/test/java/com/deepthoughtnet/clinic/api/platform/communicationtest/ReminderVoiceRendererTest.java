package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import java.util.List;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

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
    void hindiAndHinglishPreferSarvamRegardlessOfEnglishTokensInText() {
        for (String mode : List.of("HINDI", "HINGLISH")) {
            TextToSpeechProvider sarvam = provider("sarvam", true,
                    new VoiceSynthesisResult(new byte[]{1, 2}, "audio/L16", "sarvam", null));
            TextToSpeechProvider elevenLabs = provider("elevenlabs", true,
                    new VoiceSynthesisResult(new byte[]{2}, "audio/mpeg", "elevenlabs", null));

            var result = new ReminderVoiceRenderer(List.of(elevenLabs, sarvam), properties("elevenlabs", "sarvam"))
                    .render("7:30 PM doctor appointment", mode, "hi-IN");

            assertEquals("sarvam", result.provider());
            verify(sarvam).synthesize(any());
            verify(elevenLabs, never()).synthesize(any());
        }
    }

    @Test
    void englishPrefersElevenLabsBeforeSarvam() {
        TextToSpeechProvider sarvam = provider("sarvam", true,
                new VoiceSynthesisResult(new byte[]{1, 2}, "audio/L16", "sarvam", null));
        TextToSpeechProvider elevenLabs = provider("elevenlabs", true,
                new VoiceSynthesisResult(new byte[]{2}, "audio/mpeg", "elevenlabs", null));

        var result = new ReminderVoiceRenderer(List.of(sarvam, elevenLabs), properties("sarvam", "elevenlabs"))
                .render("Please confirm the appointment", "ENGLISH", "en-IN");

        assertEquals("elevenlabs", result.provider());
        verify(elevenLabs).synthesize(any());
        verify(sarvam, never()).synthesize(any());
    }

    @Test
    void selectionDiagnosticIncludesPersistedPresentationModeAndChosenProvider() {
        Logger logger = (Logger) LoggerFactory.getLogger(ReminderVoiceRenderer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            TextToSpeechProvider sarvam = provider("sarvam", true,
                    new VoiceSynthesisResult(new byte[]{1, 2}, "audio/L16", "sarvam", null));
            new ReminderVoiceRenderer(List.of(sarvam), properties()).render("नमस्ते", "HINGLISH", "hi-IN");

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("VOICE_TTS_SELECTION", "presentationMode=HINGLISH", "selectedProvider=sarvam",
                                    "selectedProviderReady=true"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void hindiFallsBackToElevenLabsWhenSarvamIsUnavailableOrFails() {
        TextToSpeechProvider unavailableSarvam = provider("sarvam", false, null);
        TextToSpeechProvider elevenLabs = provider("elevenlabs", true,
                new VoiceSynthesisResult(new byte[]{2}, "audio/mpeg", "elevenlabs", null));
        var unavailableResult = new ReminderVoiceRenderer(List.of(elevenLabs, unavailableSarvam), properties())
                .render("नमस्ते", "HINDI", "hi-IN");
        assertEquals("elevenlabs", unavailableResult.provider());
        verify(unavailableSarvam, never()).synthesize(any());

        TextToSpeechProvider failingSarvam = provider("sarvam", true, null);
        when(failingSarvam.synthesize(any())).thenThrow(new IllegalStateException("sensitive provider detail"));
        TextToSpeechProvider fallback = provider("elevenlabs", true,
                new VoiceSynthesisResult(new byte[]{3}, "audio/mpeg", "elevenlabs", null));
        var failedResult = new ReminderVoiceRenderer(List.of(fallback, failingSarvam), properties())
                .render("नमस्ते", "HINGLISH", "hi-IN");
        assertEquals("elevenlabs", failedResult.provider());
        InOrder order = inOrder(failingSarvam, fallback);
        order.verify(failingSarvam).synthesize(any());
        order.verify(fallback).synthesize(any());
    }

    @Test
    void malformedSarvamPcmFallsBackBeforePlaybackSelection() {
        TextToSpeechProvider malformedSarvam = provider("sarvam", true,
                new VoiceSynthesisResult(new byte[]{1}, "audio/L16", "sarvam", null));
        TextToSpeechProvider elevenLabs = provider("elevenlabs", true,
                new VoiceSynthesisResult(new byte[]{3, 4}, "audio/mpeg", "elevenlabs", null));

        var result = new ReminderVoiceRenderer(List.of(elevenLabs, malformedSarvam),
                properties("elevenlabs", "sarvam")).render("नमस्ते", "HINDI", "hi-IN");

        assertEquals("elevenlabs", result.provider());
        verify(malformedSarvam).synthesize(any());
        verify(elevenLabs).synthesize(any());
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
        if (order.length > 0) properties.getTts().setProviderOrder(List.of(order));
        return properties;
    }
}
