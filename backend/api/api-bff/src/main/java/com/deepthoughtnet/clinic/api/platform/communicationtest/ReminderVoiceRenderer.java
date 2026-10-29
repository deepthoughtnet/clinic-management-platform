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
        log.info("VOICE_TTS_CONFIGURATION englishProviderOrder=[elevenlabs,sarvam,piper,mock] "
                        + "hindiProviderOrder=[sarvam,elevenlabs,piper,mock] "
                        + "hinglishProviderOrder=[sarvam,elevenlabs,piper,mock] sarvamEnabled={} "
                        + "sarvamModel={} sarvamSpeaker={} sarvamTargetLanguage={} sarvamSampleRate={} "
                        + "sarvamTransport=REST sarvamReady={} elevenLabsEnabled={} piperEnabled={}",
                properties != null && properties.getSarvam().isEnabled() && properties.getSarvam().isTtsEnabled(),
                properties == null ? "bulbul:v4-flash" : properties.getSarvam().getTtsModel(),
                properties == null ? "aparna_hi_customer" : properties.getSarvam().getTtsSpeaker(),
                properties == null ? "hi-IN" : properties.getSarvam().getTtsLanguageCode(),
                properties == null ? 16_000 : properties.getSarvam().getTtsSampleRate(), ready("sarvam"),
                ready("elevenlabs"), ready("piper"));
    }

    public RenderedReminder render(String text, String language) {
        return render(text, inferPresentationMode(language), language);
    }

    public RenderedReminder render(String text, String presentationMode, String language) {
        String mode = normalizePresentationMode(presentationMode, language);
        List<String> desiredOrder = providerOrder(mode);
        List<TextToSpeechProvider> ordered = orderedProviders(mode, true);
        if (ordered.isEmpty()) return new RenderedReminder(null, "TTS_NOT_CONFIGURED", null);
        boolean providerReady = false;
        String lastFailure = "TTS_PROVIDER_UNAVAILABLE";
        String locale = StringUtils.hasText(language) ? language.trim() : "en-IN";
        TextToSpeechProvider selected = ordered.stream().filter(provider -> ready(provider.providerName())).findFirst().orElse(null);
        log.info("VOICE_TTS_SELECTION presentationMode={} requestedLanguage={} providerOrder={} selectedProvider={} selectedProviderReady={}",
                mode, locale, desiredOrder, selected == null ? "none" : providerKey(selected), selected != null);
        for (int index = 0; index < ordered.size(); index++) {
            TextToSpeechProvider provider = ordered.get(index);
            boolean ready = ready(provider.providerName());
            log.info("VOICE_TTS_PROVIDER_ATTEMPT provider={} presentationMode={} ready={}", providerKey(provider), mode, ready);
            if (!ready) continue;
            providerReady = true;
            long started = System.currentTimeMillis();
            try {
                // Reminder transport needs a decodable PCM intermediate. This request-scoped
                // preference leaves the provider's ordinary AIVA output format unchanged.
                VoiceSynthesisResult result = provider.synthesize(new VoiceSynthesisRequest(null, text, locale, "pcm_16000"));
                byte[] audio = result == null ? null : result.audioBytes();
                if (audio == null || audio.length == 0) {
                    lastFailure = "TTS_AUDIO_INVALID";
                    log.warn("VOICE_TTS_PROVIDER_FAILURE provider={} reason={} fallbackProvider={}",
                            providerKey(provider), lastFailure, nextProvider(ordered, index));
                    continue;
                }
                String contentType = StringUtils.hasText(result.contentType()) && result.contentType().length() <= 80
                        ? result.contentType() : "application/octet-stream";
                if ("sarvam".equals(providerKey(provider))) {
                    // Validate this provider's requested Linear16 contract before
                    // selecting it for playback, so invalid Sarvam output can fall
                    // through to ElevenLabs without sending partial audio.
                    ReminderAudioNormalizer.normalize(audio, contentType,
                            properties == null ? 16_000 : properties.getSarvam().getTtsSampleRate());
                }
                int sourceRate = resolveSourceSampleRate(audio, contentType, providerKey(provider));
                log.info("VOICE_TTS_PROVIDER_SUCCESS provider={} model={} speaker={} requestedSampleRate={} actualSampleRate={} "
                                + "audioFormat={} audioBytes={} latencyMs={}",
                        providerKey(provider), "sarvam".equals(providerKey(provider)) ? properties.getSarvam().getTtsModel() : "n/a",
                        "sarvam".equals(providerKey(provider)) ? properties.getSarvam().getTtsSpeaker() : "n/a",
                        "sarvam".equals(providerKey(provider)) ? properties.getSarvam().getTtsSampleRate() : 16_000,
                        sourceRate > 0 ? sourceRate : "unknown", contentType, audio.length,
                        System.currentTimeMillis() - started);
                return new RenderedReminder(new SpeechSynthesisResult(audio, contentType, 0L), null, provider.providerName(), sourceRate);
            } catch (RuntimeException ex) {
                lastFailure = "TTS_SYNTHESIS_FAILED";
                log.warn("VOICE_TTS_PROVIDER_FAILURE provider={} reason={} fallbackProvider={} latencyMs={}",
                        providerKey(provider), safeFailureCategory(ex), nextProvider(ordered, index),
                        System.currentTimeMillis() - started);
            }
        }
        return new RenderedReminder(null, providerReady ? lastFailure : "TTS_PROVIDER_UNAVAILABLE", null);
    }

    public Readiness readiness() {
        List<TextToSpeechProvider> ordered = orderedProviders("HINDI", false);
        for (TextToSpeechProvider provider : ordered) {
            try {
                if (provider.isReady()) return new Readiness("READY", provider.providerName(), ordered.stream().map(TextToSpeechProvider::providerName).toList(), null);
            } catch (RuntimeException ignored) { }
        }
        return new Readiness(ordered.isEmpty() ? "NOT_CONFIGURED" : "PARTIAL", null,
                ordered.stream().map(TextToSpeechProvider::providerName).toList(),
                ordered.isEmpty() ? "No deterministic TTS provider is configured." : "Configured TTS providers are unavailable.");
    }

    private List<TextToSpeechProvider> orderedProviders(String presentationMode, boolean allowMockFallback) {
        LinkedHashMap<String, TextToSpeechProvider> byName = new LinkedHashMap<>();
        for (TextToSpeechProvider provider : providers) {
            if (provider != null && (!isTestProvider(provider.providerName())
                    || allowMockFallback && "mock".equals(providerKey(provider)))) {
                byName.putIfAbsent(provider.providerName().toLowerCase(Locale.ROOT), provider);
            }
        }
        List<TextToSpeechProvider> ordered = new ArrayList<>();
        List<String> configured = providerOrder(presentationMode);
        for (String name : configured == null ? List.<String>of() : configured) {
            TextToSpeechProvider provider = byName.remove(name.toLowerCase(Locale.ROOT));
            if (provider != null) ordered.add(provider);
        }
        ordered.addAll(byName.values());
        return ordered;
    }

    private List<String> providerOrder(String presentationMode) {
        return "ENGLISH".equals(presentationMode)
                ? List.of("elevenlabs", "sarvam", "piper", "mock")
                : List.of("sarvam", "elevenlabs", "piper", "mock");
    }

    private String normalizePresentationMode(String mode, String language) {
        if (StringUtils.hasText(mode)) {
            String normalized = mode.trim().toUpperCase(Locale.ROOT);
            if (List.of("ENGLISH", "HINDI", "HINGLISH").contains(normalized)) return normalized;
        }
        return inferPresentationMode(language);
    }

    private String inferPresentationMode(String language) {
        return StringUtils.hasText(language) && language.trim().toLowerCase(Locale.ROOT).startsWith("en")
                ? "ENGLISH" : "HINDI";
    }

    private boolean ready(String name) {
        for (TextToSpeechProvider provider : providers) {
            if (provider != null && providerKey(provider).equalsIgnoreCase(name)) {
                try { return provider.isReady(); } catch (RuntimeException ignored) { return false; }
            }
        }
        return false;
    }

    private String providerKey(TextToSpeechProvider provider) {
        return provider == null || !StringUtils.hasText(provider.providerName())
                ? "unknown" : provider.providerName().trim().toLowerCase(Locale.ROOT);
    }

    private String nextProvider(List<TextToSpeechProvider> ordered, int index) {
        return index + 1 < ordered.size() ? providerKey(ordered.get(index + 1)) : "none";
    }

    private String safeFailureCategory(RuntimeException ex) {
        return ex == null ? "SYNTHESIS_FAILED" : ex.getClass().getSimpleName();
    }

    private int resolveSourceSampleRate(byte[] audio, String contentType, String provider) {
        int knownRate = "sarvam".equals(provider) && properties != null
                ? properties.getSarvam().getTtsSampleRate() : 16_000;
        try {
            return ReminderAudioNormalizer.normalize(audio, contentType, knownRate).sampleRate();
        } catch (RuntimeException ignored) {
            return 0;
        }
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
