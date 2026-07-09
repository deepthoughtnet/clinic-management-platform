package com.deepthoughtnet.clinic.api.ai.reasoning.dto;

import java.math.BigDecimal;

public record RecommendedTestItem(
        String name,
        String reason,
        String priority,
        String timing,
        BigDecimal confidence,
        String source,
        String observationDate,
        String sourceType,
        String sourceTitle,
        String verificationStatus,
        Boolean alreadyAvailable,
        Boolean pendingOrderExists,
        String actionType
) {
}
