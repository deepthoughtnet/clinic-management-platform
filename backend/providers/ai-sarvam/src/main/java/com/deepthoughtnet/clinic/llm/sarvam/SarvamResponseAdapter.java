package com.deepthoughtnet.clinic.llm.sarvam;

import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Normalizes Sarvam's OpenAI-compatible wire envelope without changing the logical AIVA contract. */
final class SarvamResponseAdapter {
    private SarvamResponseAdapter() {
    }

    static Parsed parse(ObjectMapper objectMapper, String body, String provider, String model) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new IllegalArgumentException("Sarvam returned no choices");
            }
            JsonNode choice = choices.get(0);
            String text = choice.path("message").path("content").asText(null);
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("Sarvam returned empty content");
            }
            JsonNode usage = root.path("usage");
            AiTokenUsage tokenUsage = usage.isMissingNode() || usage.isNull() ? null
                    : new AiTokenUsage(usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong(),
                    usage.path("total_tokens").asLong(), null);
            return new Parsed(root.path("model").asText(model), text.trim(),
                    choice.path("finish_reason").asText(null), tokenUsage);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AiProviderException.retryable("Failed to parse Sarvam response", null, provider, model,
                    "/v1/chat/completions", ex);
        }
    }

    record Parsed(String model, String text, String finishReason, AiTokenUsage tokenUsage) {
    }
}
