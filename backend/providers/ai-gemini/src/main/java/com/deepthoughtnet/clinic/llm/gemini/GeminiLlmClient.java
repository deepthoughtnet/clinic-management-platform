package com.deepthoughtnet.clinic.llm.gemini;

import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component("geminiLlmClient")
@ConditionalOnExpression(
        "'${clinic.ai.enabled:false}' == 'true' "
                + "&& '${clinic.ai.provider:DISABLED}'.equalsIgnoreCase('GEMINI') "
                + "&& '${clinic.ai.gemini.enabled:false}' == 'true' "
                + "&& '${clinic.ai.gemini.api-key:${clinic.llm.gemini.apiKey:}}' != ''"
)
public class GeminiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Double defaultTemperature;
    private final Integer defaultMaxOutputTokens;
    private final int timeoutSeconds;

    public GeminiLlmClient(
            ObjectMapper objectMapper,
            @Value("${clinic.ai.gemini.base-url:${clinic.llm.gemini.baseUrl:https://generativelanguage.googleapis.com/v1beta}}") String baseUrl,
            @Value("${clinic.ai.gemini.api-key:${clinic.llm.gemini.apiKey:}}") String apiKey,
            @Value("${clinic.ai.gemini.model:${clinic.llm.gemini.model:gemini-1.5-flash}}") String model,
            @Value("${clinic.ai.gemini.temperature:${clinic.llm.gemini.temperature:0.1}}") Double defaultTemperature,
            @Value("${clinic.ai.gemini.max-output-tokens:${clinic.llm.gemini.maxOutputTokens:2048}}") Integer defaultMaxOutputTokens,
            @Value("${clinic.ai.gemini.timeout-seconds:${clinic.ai.request-timeout-seconds:60}}") int timeoutSeconds
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.max(1, timeoutSeconds) * 1000;
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String providerName() {
        return "GEMINI";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        long startedAt = System.currentTimeMillis();
        String requestId = correlationOrGenerated();
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
        log.info("Calling Gemini provider. requestId={}, model={}, chars={}, hasAttachment={}, timeoutSeconds={}",
                requestId,
                model,
                request.userPrompt().length(),
                request.bytes() != null && request.bytes().length > 0,
                timeoutSeconds);

        try {
            String responseBody = restClient.post()
                    .uri(baseUrl + "/models/" + model + ":generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            ParsedGeminiResponse parsed = extractResponse(responseBody);
            long latencyMs = System.currentTimeMillis() - startedAt;
            log.info(
                    "Gemini response received. requestId={}, status=200, latencyMs={}, responseChars={}, candidates={}, finishReason={}, safetyBlocked={}, tokenUsage={}",
                    requestId,
                    latencyMs,
                    parsed.responseChars(),
                    parsed.candidateCount(),
                    parsed.finishReason(),
                    parsed.safetyBlocked(),
                    formatTokenUsage(parsed.tokenUsage())
            );

            if (log.isDebugEnabled()) {
                log.debug("Gemini preview. requestId={}, text=\"{}\"", requestId, safePreview(parsed.text()));
            }
            if ("MAX_TOKENS".equalsIgnoreCase(parsed.finishReason())) {
                log.warn("Gemini response truncated by max output tokens. requestId={}, maxOutputTokens={}",
                        requestId,
                        request.maxOutputTokens() != null ? request.maxOutputTokens() : defaultMaxOutputTokens);
            }

            if (parsed.text() == null || parsed.text().isBlank()) {
                log.warn("LLM provider returned empty content. provider=GEMINI, requestId={}", requestId);
                throw new IllegalStateException("Gemini returned an empty response");
            }
            return new LlmResponse(providerName(), model, parsed.text().trim(), parsed.tokenUsage());
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.error("Gemini authentication failed. requestId={}, status={}, message={}", requestId, ex.getStatusCode().value(), sanitize(ex.getStatusText()));
            throw new IllegalStateException("Gemini authentication failed: invalid API key.");
        } catch (HttpClientErrorException.Forbidden ex) {
            log.error("Gemini authorization failed. requestId={}, status={}, message={}", requestId, ex.getStatusCode().value(), sanitize(ex.getStatusText()));
            throw new IllegalStateException("Gemini authorization failed.");
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.error("Gemini quota exceeded. requestId={}, status={}, message={}", requestId, ex.getStatusCode().value(), sanitize(ex.getStatusText()));
            throw new IllegalStateException("Gemini quota exceeded.");
        } catch (HttpClientErrorException ex) {
            log.error("Gemini HTTP client error. requestId={}, status={}, message={}", requestId, ex.getStatusCode().value(), sanitize(ex.getStatusText()));
            throw new IllegalStateException("Gemini request failed with client error.");
        } catch (HttpServerErrorException ex) {
            log.error("Gemini provider unavailable. requestId={}, status={}, message={}", requestId, ex.getStatusCode().value(), sanitize(ex.getStatusText()));
            throw new IllegalStateException("Gemini provider unavailable.");
        } catch (RestClientResponseException ex) {
            log.error("Gemini HTTP failure. requestId={}, status={}, message={}", requestId, ex.getRawStatusCode(), sanitize(ex.getStatusText()));
            throw new IllegalStateException("Gemini request failed.");
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.error("Gemini timeout. requestId={}, timeoutSeconds={}", requestId, timeoutSeconds);
                throw new IllegalStateException("Gemini request timed out.");
            }
            log.error("Gemini network failure. requestId={}, message={}", requestId, sanitize(ex.getMessage()));
            throw new IllegalStateException("Gemini network failure.");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Gemini unexpected failure. requestId={}, message={}", requestId, sanitize(ex.getMessage()));
            throw new IllegalStateException("Gemini provider failed unexpectedly.");
        }
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
        generationConfig.put("responseMimeType", "application/json");
        payload.put("generationConfig", generationConfig);

        return payload;
    }

    private ParsedGeminiResponse extractResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            log.warn("Gemini returned empty response body.");
            return new ParsedGeminiResponse(null, 0, 0, null, false, null);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            boolean safetyBlocked = root.path("promptFeedback").path("blockReason").isTextual();
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                log.warn("Gemini returned empty candidate list.");
                return new ParsedGeminiResponse(null, responseBody.length(), 0, null, safetyBlocked, tokenUsage(root));
            }

            StringBuilder builder = new StringBuilder();
            for (JsonNode candidate : candidates) {
                JsonNode parts = candidate.path("content").path("parts");
                if (!parts.isArray() || parts.isEmpty()) {
                    continue;
                }
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
            }
            String extracted = builder.toString();
            if (extracted.isBlank()) {
                log.warn("Gemini candidate content had no text.");
            }
            return new ParsedGeminiResponse(extracted, responseBody.length(), candidates.size(), finishReason(candidates), safetyBlocked, tokenUsage(root));
        } catch (Exception ex) {
            String preview = sanitizePreview(responseBody, 300);
            log.error("Gemini parsing failure. message={}, responsePreview=\"{}\"", sanitize(ex.getMessage()), preview);
            throw new IllegalStateException("Failed to parse Gemini response", ex);
        }
    }

    private String finishReason(JsonNode candidates) {
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(0).path("finishReason").asText(null);
    }

    private AiTokenUsage tokenUsage(JsonNode root) {
        JsonNode usage = root.path("usageMetadata");
        if (!usage.isObject()) {
            return null;
        }
        Long prompt = usage.path("promptTokenCount").canConvertToLong() ? usage.path("promptTokenCount").asLong() : null;
        Long completion = usage.path("candidatesTokenCount").canConvertToLong() ? usage.path("candidatesTokenCount").asLong() : null;
        Long total = usage.path("totalTokenCount").canConvertToLong() ? usage.path("totalTokenCount").asLong() : null;
        if (prompt == null && completion == null && total == null) {
            return null;
        }
        return new AiTokenUsage(prompt, completion, total, null);
    }

    private String formatTokenUsage(AiTokenUsage usage) {
        if (usage == null) {
            return "n/a";
        }
        return "prompt=" + safeInt(usage.promptTokens()) + ",completion=" + safeInt(usage.completionTokens()) + ",total=" + safeInt(usage.totalTokens());
    }

    private String safeInt(Long value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private boolean isTimeout(ResourceAccessException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return cleaned.length() > 240 ? cleaned.substring(0, 240) + "..." : cleaned;
    }

    private String sanitizePreview(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String preview = value.length() > maxChars ? value.substring(0, maxChars) : value;
        preview = preview.replaceAll("[\\r\\n\\t]+", " ");
        preview = preview.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[redacted-email]");
        preview = preview.replaceAll("\\b\\+?[0-9][0-9\\-\\s()]{7,}[0-9]\\b", "[redacted-phone]");
        return preview;
    }

    private String safePreview(String text) {
        return sanitizePreview(text, 400);
    }

    private String correlationOrGenerated() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("X-Correlation-Id");
        }
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }

    private record ParsedGeminiResponse(
            String text,
            int responseChars,
            int candidateCount,
            String finishReason,
            boolean safetyBlocked,
            AiTokenUsage tokenUsage
    ) {
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
