package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.util.Locale;

public final class AiFinishReasonNormalizer {
    private AiFinishReasonNormalizer() {
    }

    public static String normalize(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = finishReason.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "STOP" -> "COMPLETE";
            case "MAX_TOKENS", "LENGTH", "MAX_TOKENS_EXCEEDED" -> "TRUNCATED";
            case "SAFETY", "CONTENT_FILTER" -> "BLOCKED";
            case "ERROR" -> "FAILED";
            case "UNKNOWN" -> "UNKNOWN";
            case "COMPLETE", "TRUNCATED", "BLOCKED", "FAILED" -> normalized;
            default -> normalized;
        };
    }

    public static boolean isComplete(String finishReason) {
        return "COMPLETE".equalsIgnoreCase(normalize(finishReason));
    }

    public static boolean isTruncated(String finishReason) {
        return "TRUNCATED".equalsIgnoreCase(normalize(finishReason));
    }

    public static boolean isBlocked(String finishReason) {
        return "BLOCKED".equalsIgnoreCase(normalize(finishReason));
    }

    public static boolean isFailed(String finishReason) {
        return "FAILED".equalsIgnoreCase(normalize(finishReason));
    }
}
