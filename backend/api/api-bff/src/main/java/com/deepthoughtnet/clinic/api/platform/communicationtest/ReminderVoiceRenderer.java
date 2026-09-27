package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.deepthoughtnet.clinic.tts.spi.SpeechSynthesisResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Provider-neutral deterministic TTS facade for platform reminder tests. */
@Service
public class ReminderVoiceRenderer {
    private static final Logger log = LoggerFactory.getLogger(ReminderVoiceRenderer.class);
    private final List<TextToSpeechProvider> providers;
    private final VoiceTestProperties properties;

    public ReminderVoiceRenderer(List<TextToSpeechProvider> providers, VoiceTestProperties properties) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.properties = properties;
        log.info("REMINDER_TTS_RESOLUTION availableProviders={} providerOrder={}",
                this.providers.stream().map(TextToSpeechProvider::providerName).toList(),
                properties == null ? List.of() : properties.getTts().getProviderOrder());
    }

    public RenderedReminder render(String text, String language) {
        List<TextToSpeechProvider> ordered = orderedProviders();
        if (ordered.isEmpty()) return new RenderedReminder(null, "TTS_NOT_CONFIGURED", null);
        boolean providerReady = false;
        String lastFailure = "TTS_PROVIDER_UNAVAILABLE";
        String locale = StringUtils.hasText(language) ? language.trim() : "en-IN";
        for (TextToSpeechProvider provider : ordered) {
            boolean ready;
            try { ready = provider.isReady(); } catch (RuntimeException ex) { ready = false; }
            if (!ready) continue;
            providerReady = true;
            long started = System.currentTimeMillis();
            log.info("REMINDER_TTS_RESOLUTION requestedLanguage={} selectedProvider={} selectedProviderReady=true",
                    locale, provider.providerName());
            try {
                // Reminder transport needs a decodable PCM intermediate. This request-scoped
                // preference does not alter the existing AIVA ElevenLabs default (MP3).
                VoiceSynthesisResult result = provider.synthesize(new VoiceSynthesisRequest(null, text, locale, "pcm_16000"));
                byte[] audio = result == null ? null : result.audioBytes();
                if (audio == null || audio.length == 0) {
                    lastFailure = "TTS_AUDIO_INVALID";
                    continue;
                }
                String contentType = StringUtils.hasText(result.contentType()) && result.contentType().length() <= 80
                        ? result.contentType() : "application/octet-stream";
                int sourceRate = contentType.toLowerCase(Locale.ROOT).contains("pcm") ? 16_000 : 0;
                String format = sourceRate > 0 ? "PCM16" : contentType;
                log.info("REMINDER_TTS_RESULT provider={} success=true audioBytes={} contentType={} format={} sampleRate={} elapsedMs={}",
                        provider.providerName(), audio.length, contentType, format, sourceRate, System.currentTimeMillis() - started);
                return new RenderedReminder(new SpeechSynthesisResult(audio, contentType, 0L), null, provider.providerName(), sourceRate);
            } catch (RuntimeException ex) {
                lastFailure = "TTS_SYNTHESIS_FAILED";
                log.warn("REMINDER_TTS_RESULT provider={} success=false failureCategory={} elapsedMs={}",
                        provider.providerName(), lastFailure, System.currentTimeMillis() - started);
            }
        }
        return new RenderedReminder(null, providerReady ? lastFailure : "TTS_PROVIDER_UNAVAILABLE", null);
    }

    public Readiness readiness() {
        List<TextToSpeechProvider> ordered = orderedProviders();
        for (TextToSpeechProvider provider : ordered) {
            try {
                if (provider.isReady()) return new Readiness("READY", provider.providerName(), ordered.stream().map(TextToSpeechProvider::providerName).toList(), null);
            } catch (RuntimeException ignored) { }
        }
        return new Readiness(ordered.isEmpty() ? "NOT_CONFIGURED" : "PARTIAL", null,
                ordered.stream().map(TextToSpeechProvider::providerName).toList(),
                ordered.isEmpty() ? "No deterministic TTS provider is configured." : "Configured TTS providers are unavailable.");
    }

    private List<TextToSpeechProvider> orderedProviders() {
        LinkedHashMap<String, TextToSpeechProvider> byName = new LinkedHashMap<>();
        for (TextToSpeechProvider provider : providers) {
            if (provider != null && !isTestProvider(provider.providerName())) {
                byName.putIfAbsent(provider.providerName().toLowerCase(Locale.ROOT), provider);
            }
        }
        List<TextToSpeechProvider> ordered = new ArrayList<>();
        List<String> configured = properties == null ? List.of() : properties.getTts().getProviderOrder();
        for (String name : configured == null ? List.<String>of() : configured) {
            TextToSpeechProvider provider = byName.remove(name.toLowerCase(Locale.ROOT));
            if (provider != null) ordered.add(provider);
        }
        ordered.addAll(byName.values());
        return ordered;
    }

    private boolean isTestProvider(String name) {
        if (!StringUtils.hasText(name)) return true;
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.matches(".*(?:mock|test|fake|stub).*");
    }

    public record RenderedReminder(SpeechSynthesisResult audio, String failureCategory, String provider, int sourceSampleRate) {
        public RenderedReminder(SpeechSynthesisResult audio, String failureCategory, String provider) {
            this(audio, failureCategory, provider, 0);
        }
    }
    public record Readiness(String status, String provider, List<String> availableProviders, String message) {}
}
