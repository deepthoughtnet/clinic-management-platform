package com.deepthoughtnet.clinic.api.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record AiDraftResponse(
        boolean enabled,
        boolean fallbackUsed,
        String message,
        String draft,
        Map<String, Object> structuredData,
        BigDecimal confidence,
        List<String> suggestedActions,
        List<String> warnings
) {
}
