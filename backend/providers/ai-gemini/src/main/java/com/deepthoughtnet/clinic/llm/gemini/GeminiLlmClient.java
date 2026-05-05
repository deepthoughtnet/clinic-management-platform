package com.deepthoughtnet.clinic.llm.gemini;

import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component("geminiLlmClient")
public class GeminiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Double defaultTemperature;
    private final Integer defaultMaxOutputTokens;

    public GeminiLlmClient(
            ObjectMapper objectMapper,
            @Value("${clinic.llm.gemini.baseUrl:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${clinic.llm.gemini.apiKey:}") String apiKey,
            @Value("${clinic.llm.gemini.model:gemini-1.5-flash}") String model,
            @Value("${clinic.llm.gemini.temperature:0.1}") Double defaultTemperature,
            @Value("${clinic.llm.gemini.maxOutputTokens:2048}") Integer defaultMaxOutputTokens
    ) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
    }

    @Override
    public String providerName() {
        return "GEMINI";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        if (request.userPrompt() == null || request.userPrompt().isBlank()) {
            throw new IllegalArgumentException("userPrompt is required");
        }

        Map<String, Object> payload = buildPayload(request);
        log.info("Calling Gemini LLM provider. model={}, userPromptChars={}, hasAttachment={}",
                model,
                request.userPrompt().length(),
                request.bytes() != null && request.bytes().length > 0);

        String responseBody = restClient.post()
                .uri(baseUrl + "/models/" + model + ":generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

        String text = extractText(responseBody);

        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Gemini returned an empty response");
        }

        log.info("Gemini LLM provider completed. model={}, responseChars={}", model, text.trim().length());
        return new LlmResponse(providerName(), model, text.trim(), null);
    }

    private Map<String, Object> buildPayload(LlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.systemPrompt()))
            ));
        }

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", request.userPrompt()));

        if (request.bytes() != null && request.bytes().length > 0) {
            String mediaType = normalizeMediaType(request.mediaType());
            parts.add(Map.of(
                    "inlineData", Map.of(
                            "mimeType", mediaType,
                            "data", Base64.getEncoder().encodeToString(request.bytes())
                    )
            ));
        }

        payload.put("contents", List.of(Map.of("role", "user", "parts", parts)));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", request.temperature() != null ? request.temperature() : defaultTemperature);
        generationConfig.put(
                "maxOutputTokens",
                request.maxOutputTokens() != null ? request.maxOutputTokens() : defaultMaxOutputTokens
        );
        payload.put("generationConfig", generationConfig);

        return payload;
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return null;
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return null;
            }

            StringBuilder builder = new StringBuilder();
            for (JsonNode part : parts) {
                String text = part.path("text").asText(null);
                if (text == null || text.isBlank()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(text);
            }

            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Gemini response", ex);
        }
    }

    private String normalizeMediaType(String value) {
        if (value == null || value.isBlank()) {
            return "application/octet-stream";
        }
        return value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
