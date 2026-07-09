package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.math.BigDecimal;

public record EvidenceItem(
        String text,
        String source,
        String observationDate,
        BigDecimal confidence,
        String type,
        String sourceType,
        String sourceTitle,
        String verificationStatus
) {
}
