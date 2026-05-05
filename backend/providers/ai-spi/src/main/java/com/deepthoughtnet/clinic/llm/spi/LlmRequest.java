package com.deepthoughtnet.clinic.llm.spi;

public record LlmRequest(
        String systemPrompt,
        String userPrompt,
        String originalFilename,
        String mediaType,
        byte[] bytes,
        Double temperature,
        Integer maxOutputTokens
) {
}