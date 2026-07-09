package com.deepthoughtnet.clinic.llm.spi;

import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;

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
        boolean strictJsonMode
) {
    public LlmRequest(String systemPrompt,
                      String userPrompt,
                      String originalFilename,
                      String mediaType,
                      byte[] bytes,
                      Double temperature,
                      Integer maxOutputTokens) {
        this(systemPrompt, userPrompt, originalFilename, mediaType, bytes, temperature, maxOutputTokens, null, null, null, false);
    }
}
