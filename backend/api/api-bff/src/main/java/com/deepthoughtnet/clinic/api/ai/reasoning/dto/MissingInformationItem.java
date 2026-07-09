package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.math.BigDecimal;

public record MissingInformationItem(
        String name,
        String whyItMatters,
        String requestedAction,
        BigDecimal confidence
) {
}
