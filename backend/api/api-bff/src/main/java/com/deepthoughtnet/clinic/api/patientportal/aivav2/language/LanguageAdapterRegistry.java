package com.deepthoughtnet.clinic.api.patientportal.aivav2.language;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Deterministic detector; never depends on Spring's collection injection order. */
@Component
public class LanguageAdapterRegistry {
    private static final Pattern MARATHI_SIGNAL = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{M}])(?:मला|माझ्या|सहकार्य|हवे)(?![\\p{L}\\p{M}])");
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
        // Do not run Devanagari text from another Indic language through the Hindi
        // name transliterator. We do not have language-specific parsers for those
        // scripts yet, so let the ordinary unsupported-input recovery ask again.
        if (containsUnsupportedIndicScript(text) || MARATHI_SIGNAL.matcher(text).find()) return english;
        // The current utterance controls response language; a stale Hindi caller
        // preference must not force an English sentence into Hinglish.
        if (hindi.hasHindiUtteranceSignal(text)) return hindi;
        if (english.isClearlyEnglish(text)) return english;
        if (hindi.supports(requested, text)) return hindi;
        if (english.supports(requested, text)) return english;
        // Unknown/blank input has a stable fallback after Hindi detection has run.
        return english;
    }

    private boolean containsUnsupportedIndicScript(String text) {
        return text.codePoints().anyMatch(codePoint -> {
            if (!Character.isLetter(codePoint)) return false;
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            return script != Character.UnicodeScript.DEVANAGARI
                    && script != Character.UnicodeScript.LATIN;
        });
    }
}
