package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.util.List;

/** Bounded evidence contract at the V2 Voice interpretation boundary. */
public record VoiceRecognitionResult(
        Action action,
        Quality quality,
        Source source,
        String ambiguity,
        List<String> candidates,
        boolean clarificationRecommended
) {
    public enum Quality { EXACT, STRONG, AMBIGUOUS, NO_MATCH }
    public enum Source { LANGUAGE_ADAPTER, CURRENT_CONTEXT, DISPLAYED_CHOICES, CANONICAL_PROVIDER, AI_PROPOSAL }
    public enum Action {
        SHOW_MORE, SELECT_SLOT, CONFIRM, NEGATE, PROVIDER_EVIDENCE, PROVIDER_CHANGE,
        DATE_EVIDENCE, DAYPART_EVIDENCE, CLARIFY, NONE
    }

    public VoiceRecognitionResult {
        action = action == null ? Action.NONE : action;
        quality = quality == null ? Quality.NO_MATCH : quality;
        source = source == null ? Source.LANGUAGE_ADAPTER : source;
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static VoiceRecognitionResult of(Action action, Quality quality, Source source) {
        return new VoiceRecognitionResult(action, quality, source, null, List.of(),
                quality == Quality.AMBIGUOUS || quality == Quality.NO_MATCH);
    }
}
