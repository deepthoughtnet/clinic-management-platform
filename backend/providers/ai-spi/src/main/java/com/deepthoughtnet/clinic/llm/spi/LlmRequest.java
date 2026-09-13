package com.deepthoughtnet.clinic.llm.spi;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import java.util.Map;

public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        String originalFilename,
        String mediaType,
        byte[] bytes,
        Double temperature,
        Integer maxOutputTokens,
        AiTaskType taskType,
        String modelOverride,
        Integer thinkingBudget,
        boolean strictJsonMode,
        Map<String, Object> structuredOutputSchema
) {
    public LlmRequest(String systemPrompt,
                      String userPrompt,
                      String originalFilename,
                      String mediaType,
                      byte[] bytes,
                      Double temperature,
                      Integer maxOutputTokens) {
        this(systemPrompt, userPrompt, originalFilename, mediaType, bytes, temperature, maxOutputTokens,
                null, null, null, false, null);
    }

    public LlmRequest(String systemPrompt, String userPrompt, String originalFilename, String mediaType,
                      byte[] bytes, Double temperature, Integer maxOutputTokens, AiTaskType taskType,
                      String modelOverride, Integer thinkingBudget, boolean strictJsonMode) {
        this(systemPrompt, userPrompt, originalFilename, mediaType, bytes, temperature, maxOutputTokens,
                taskType, modelOverride, thinkingBudget, strictJsonMode, null);
    }
}
