package com.deepthoughtnet.clinic.platform.contracts.ai;

import java.math.BigDecimal;

public record AiProviderResponse(
        String providerName,
        String model,
        String outputText,
        String structuredJson,
        BigDecimal confidence,
        AiTokenUsage tokenUsage
) {
}
