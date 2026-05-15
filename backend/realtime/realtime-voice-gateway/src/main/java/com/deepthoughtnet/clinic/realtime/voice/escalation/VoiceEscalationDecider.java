package com.deepthoughtnet.clinic.realtime.voice.escalation;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lightweight escalation trigger detector for realtime safety and handoff readiness.
 */
@Component
public class VoiceEscalationDecider {
    private static final Set<String> ESCALATION_KEYWORDS = Set.of(
            "human", "agent", "representative", "manager", "complaint"
    );
    private static final Set<String> EMERGENCY_KEYWORDS = Set.of(
            "emergency", "ambulance", "chest pain", "can't breathe", "suicidal"
    );

    public String escalationReason(String userText, String aiText, int misunderstandingCount, Double aiConfidence) {
        String normalizedUser = normalize(userText);
        if (containsAny(normalizedUser, EMERGENCY_KEYWORDS)) {
            return "Emergency keyword detected";
        }
        if (containsAny(normalizedUser, ESCALATION_KEYWORDS)) {
            return "User requested human handoff";
        }
        if (misunderstandingCount >= 3) {
            return "Repeated misunderstanding";
        }
        if (aiConfidence != null && aiConfidence < 0.4d) {
            return "Low AI confidence";
        }
        String normalizedAi = normalize(aiText);
        if (normalizedAi.contains("cannot") && normalizedAi.contains("assist")) {
            return "AI confidence failure";
        }
        return null;
    }

    private boolean containsAny(String text, Set<String> keywords) {
        return keywords.stream().anyMatch(text::contains);
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
