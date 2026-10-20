package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Deterministic detector; never depends on Spring's collection injection order. */
@Component
public class LanguageAdapterRegistry {
    private final HindiLanguageAdapter hindi;
    private final EnglishLanguageAdapter english;

    public LanguageAdapterRegistry(List<AivaLanguageAdapter> adapters) {
        this.hindi = adapters.stream().filter(HindiLanguageAdapter.class::isInstance)
                .map(HindiLanguageAdapter.class::cast).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Hindi language adapter is required"));
        this.english = adapters.stream().filter(EnglishLanguageAdapter.class::isInstance)
                .filter(adapter -> !(adapter instanceof HindiLanguageAdapter))
                .map(EnglishLanguageAdapter.class::cast).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("English language adapter is required"));
    }

    public static LanguageAdapterRegistry defaults() {
        return new LanguageAdapterRegistry(List.of(new HindiLanguageAdapter(), new EnglishLanguageAdapter()));
    }

    public AivaLanguageAdapter resolve(String requestedLanguage, String rawText) {
        String text = rawText == null ? "" : rawText;
        String requested = requestedLanguage == null ? "" : requestedLanguage.trim().toLowerCase(Locale.ROOT);
        // The current utterance controls response language; a stale Hindi caller
        // preference must not force an English sentence into Hinglish.
        if (hindi.hasHindiUtteranceSignal(text)) return hindi;
        if (english.isClearlyEnglish(text)) return english;
        if (hindi.supports(requested, text)) return hindi;
        if (english.supports(requested, text)) return english;
        // Unknown/blank input has a stable fallback after Hindi detection has run.
        return english;
    }
}
