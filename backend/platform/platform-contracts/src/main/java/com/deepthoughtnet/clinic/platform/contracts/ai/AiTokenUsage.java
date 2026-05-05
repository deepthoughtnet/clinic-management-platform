package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.math.BigDecimal;

public record AiTokenUsage(
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        BigDecimal estimatedCost
) {
}
