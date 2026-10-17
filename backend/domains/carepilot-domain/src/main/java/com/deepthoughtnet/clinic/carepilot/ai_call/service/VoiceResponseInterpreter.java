package com.deepthoughtnet.clinic.carepilot.ai_call.service;

import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceResponseIntent;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deterministic confirmation classifier. Phrase lists are centralized and extensible. */
@Component
public class VoiceResponseInterpreter {
    private static final List<String> NO_PHRASES = List.of(
            "i can't come", "i cannot come", "not possible", "nahi aaunga", "nahi aaungi",
            "nahi ho payega", "cancel kar do", "जी नहीं", "नहीं", "नही", "ना", "nahin ji",
            "nahin", "nahi", "nope", "decline", "cancel", "no", "na"
    );
    private static final List<String> YES_PHRASES = List.of(
            "yes i will", "i will come", "haan aaunga", "haan aaungi", "ji haan", "bilkul",
            "theek hai", "sure", "confirmed", "confirm", "okay", "ok", "yeah", "yep", "haan ji",
            "हां जी", "हाँ जी", "जी हाँ", "जी", "बिल्कुल", "ठीक है", "हां", "हाँ", "हा", "haan", "han", "haa", "ha", "ji", "yes"
    );
    private static final Set<String> UNCLEAR_PHRASES = Set.of(
            "maybe", "perhaps", "i don't know", "later", "someone else will come", "call me later"
    );

    public VoiceResponseIntent interpret(String transcript) {
        String value = normalize(transcript);
        if (value.isBlank()) return VoiceResponseIntent.NO_RESPONSE;
        if (UNCLEAR_PHRASES.stream().anyMatch(value::contains)) return VoiceResponseIntent.UNCLEAR;
        if (matches(NO_PHRASES, value)) return VoiceResponseIntent.NO;
        if (matches(YES_PHRASES, value)) return VoiceResponseIntent.YES;
        return VoiceResponseIntent.UNCLEAR;
    }

    /** Exposes the same deterministic normalization used by classification for test diagnostics. */
    public String normalizeForClassification(String transcript) {
        return normalize(transcript);
    }

    private boolean matches(List<String> phrases, String value) {
        return phrases.stream().anyMatch(phrase -> value.equals(phrase) || value.contains(" " + phrase)
                || value.startsWith(phrase + " "));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                // Devanagari vowel signs and virama are combining marks; retain them.
                .replaceAll("[^\\p{L}\\p{N}\\p{M}']+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
