package com.deepthoughtnet.clinic.ai.orchestration.service.impl;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiProvider;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderRequest;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiProviderStatus;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression(
        "'${clinic.ai.enabled:false}' == 'true' "
                + "&& '${clinic.ai.provider:DISABLED}'.equalsIgnoreCase('MOCK')"
)
public class MockAiProvider implements AiProvider {
    private static final String MODEL = "mock-clinic-ai";

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public boolean supports(AiTaskType taskType) {
        return taskType != null;
    }

    @Override
    public AiProviderResponse complete(AiProviderRequest request) {
        String output = """
                {
                  "summary": "Mock AI provider is active. No external model was called.",
                  "suggestions": [
                    "Configure Gemini for live AI output when ready.",
                    "AI suggestions are assistive only and must be clinically reviewed."
                  ],
                  "limitations": [
                    "Mock output is for local development only.",
                    "No autonomous diagnosis or prescription finalization was performed."
                  ]
                }
                """;
        return new AiProviderResponse(
                providerName(),
                MODEL,
                output,
                output,
                new BigDecimal("0.30"),
                null
        );
    }

    @Override
    public AiProviderStatus status() {
        return AiProviderStatus.AVAILABLE;
    }
}
