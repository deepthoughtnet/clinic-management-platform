package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.math.BigDecimal;

public record RedFlagItem(
        String name,
        String reason,
        String severity,
        String action,
        BigDecimal confidence,
        String source,
        String observationDate,
        String sourceType,
        String sourceTitle,
        String verificationStatus
) {
}
