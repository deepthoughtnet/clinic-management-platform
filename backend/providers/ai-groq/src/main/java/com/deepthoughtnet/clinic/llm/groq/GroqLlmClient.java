package com.deepthoughtnet.clinic.llm.groq;

import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiFinishReasonNormalizer;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTokenUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component("groqLlmClient")
@ConditionalOnExpression(
        "'${clinic.ai.enabled:false}' == 'true' "
                + "&& '${clinic.ai.groq.enabled:false}' == 'true' "
                + "&& '${clinic.ai.groq.api-key:${groq.apiKey:}}' != ''"
)
public class GroqLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GroqLlmClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Double defaultTemperature;
    private final Integer defaultMaxOutputTokens;
    private final int timeoutSeconds;

    public GroqLlmClient(
            ObjectMapper objectMapper,
            @Value("${clinic.ai.groq.base-url:${groq.baseUrl:https://api.groq.com/openai/v1}}") String baseUrl,
            @Value("${clinic.ai.groq.api-key:${groq.apiKey:}}") String apiKey,
            @Value("${clinic.ai.groq.model:${groq.model:llama-3.1-8b-instant}}") String model,
            @Value("${clinic.ai.groq.temperature:${groq.temperature:0.1}}") Double defaultTemperature,
            @Value("${clinic.ai.groq.max-output-tokens:${groq.maxOutputTokens:2048}}") Integer defaultMaxOutputTokens,
            @Value("${clinic.ai.groq.timeout-seconds:${clinic.ai.request-timeout-seconds:60}}") int timeoutSeconds
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
        return "GROQ";
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        long startedAt = System.currentTimeMillis();
        String requestId = correlationOrGenerated();
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw AiProviderException.fatal(
                    "Groq API key is not configured",
                    null,
                    providerName(),
                    model,
                    "/chat/completions",
                    null
            );
        }
        if (request.userPrompt() == null || request.userPrompt().isBlank()) {
            throw new IllegalArgumentException("userPrompt is required");
        }

        Map<String, Object> payload = buildPayload(request);
        String effectiveModel = request.modelOverride() != null && !request.modelOverride().isBlank() ? request.modelOverride() : model;
        log.info("[AI-REQUEST] taskType={} provider=GROQ model={} maxOutputTokens={} thinkingBudget={} strictJsonMode={} chars={}",
                request.taskType(),
                effectiveModel,
                request.maxOutputTokens() != null ? request.maxOutputTokens() : defaultMaxOutputTokens,
                request.thinkingBudget(),
                request.strictJsonMode(),
                request.userPrompt().length());
        log.info("Calling Groq provider. requestId={}, model={}, chars={}, hasAttachment={}, timeoutSeconds={}",
                requestId,
                effectiveModel,
                request.userPrompt().length(),
                request.bytes() != null && request.bytes().length > 0,
                timeoutSeconds);

        try {
            String responseBody = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            ParsedGroqResponse parsed = extractResponse(responseBody);
            long latencyMs = System.currentTimeMillis() - startedAt;
            log.info("[LLM-RAW] provider=GROQ rawChars={} first300Chars=\"{}\" last300Chars=\"{}\" finishReason={} candidateCount={} safetyBlocked={}",
                    responseBody == null ? 0 : responseBody.length(),
                    previewStart(responseBody),
                    previewEnd(responseBody),
                    parsed.finishReason(),
                    parsed.text() == null || parsed.text().isBlank() ? 0 : 1,
                    false);
            log.info(
                    "Groq response received. requestId={}, status=200, latencyMs={}, responseChars={}, finishReason={}, tokenUsage={}",
                    requestId,
                    latencyMs,
                    parsed.responseChars(),
                    parsed.finishReason(),
                    formatTokenUsage(parsed.tokenUsage())
            );

            if (log.isDebugEnabled()) {
                log.debug("Groq preview. requestId={}, text=\"{}\"", requestId, safePreview(parsed.text()));
            }
            log.info("[NORMALIZED] provider=GROQ normalizedChars={} first300Chars=\"{}\" last300Chars=\"{}\" finishReason={} tokenUsage={}",
                    parsed.text() == null ? 0 : parsed.text().length(),
                    previewStart(parsed.text()),
                    previewEnd(parsed.text()),
                    parsed.finishReason(),
                    formatTokenUsage(parsed.tokenUsage()));
            if ("length".equalsIgnoreCase(parsed.finishReason())) {
                log.warn("Groq response truncated by max output tokens. requestId={}, maxOutputTokens={}",
                        requestId,
                        request.maxOutputTokens() != null ? request.maxOutputTokens() : defaultMaxOutputTokens);
            }

            if (parsed.text() == null || parsed.text().isBlank()) {
                log.warn("LLM provider returned empty content. provider=GROQ, requestId={}", requestId);
                throw AiProviderException.retryable(
                        "Groq returned an empty response",
                        null,
                        providerName(),
                        model,
                        "/chat/completions",
                        null
                );
            }
            String text = parsed.text().trim();
            return new LlmResponse(
                    providerName(),
                    model,
                    text,
                    parsed.tokenUsage(),
                    parsed.finishReason(),
                    AiFinishReasonNormalizer.normalize(parsed.finishReason()),
                    text.length(),
                    text,
                    "UNKNOWN");
        } catch (RestClientResponseException ex) {
            int status = ex.getRawStatusCode();
            String bodyPreview = sanitizePreview(ex.getResponseBodyAsString(), 300);
            log.error("Groq request failed. requestId={}, provider=GROQ, model={}, endpointPath=/chat/completions, status={}, bodyPreview=\"{}\"",
                    requestId,
                    model,
                    status,
                    bodyPreview);
            throw mapGroqException(status, ex);
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                log.error("Groq timeout. requestId={}, provider=GROQ, model={}, endpointPath=/chat/completions, timeoutSeconds={}",
                        requestId, model, timeoutSeconds);
                throw AiProviderException.retryable(
                        "Groq request timed out.",
                        null,
                        providerName(),
                        model,
                        "/chat/completions",
                        ex
                );
            }
            log.error("Groq network failure. requestId={}, provider=GROQ, model={}, endpointPath=/chat/completions, message={}",
                    requestId, model, sanitize(ex.getMessage()));
            throw AiProviderException.retryable(
                    "Groq network failure.",
                    null,
                    providerName(),
                    model,
                    "/chat/completions",
                    ex
            );
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Groq unexpected failure. requestId={}, provider=GROQ, model={}, endpointPath=/chat/completions, message={}",
                    requestId,
                    model,
                    sanitize(ex.getMessage()));
            throw AiProviderException.retryable(
                    "Groq provider failed unexpectedly.",
                    null,
                    providerName(),
                    model,
                    "/chat/completions",
                    ex
            );
        }
    }

    private Map<String, Object> buildPayload(LlmRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();

        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", request.systemPrompt()),
                    Map.of("role", "user", "content", request.userPrompt())
            ));
        } else {
            payload.put("messages", List.of(Map.of("role", "user", "content", request.userPrompt())));
        }

        payload.put("model", request.modelOverride() != null && !request.modelOverride().isBlank() ? request.modelOverride() : model);
        payload.put("temperature", request.temperature() != null ? request.temperature() : defaultTemperature);
        payload.put("max_tokens", request.maxOutputTokens() != null ? request.maxOutputTokens() : defaultMaxOutputTokens);

        return payload;
    }

    private ParsedGroqResponse extractResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            log.warn("Groq returned empty response body.");
            return new ParsedGroqResponse(null, 0, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("Groq returned empty choice list.");
                return new ParsedGroqResponse(null, responseBody.length(), null, tokenUsage(root));
            }
            JsonNode firstChoice = choices.get(0);
            String content = firstChoice.path("message").path("content").asText(null);
            String finishReason = firstChoice.path("finish_reason").asText(null);
            return new ParsedGroqResponse(content, responseBody.length(), finishReason, tokenUsage(root));
        } catch (Exception ex) {
            log.error("Groq parsing failure. message={}, responsePreview=\"{}\"", sanitize(ex.getMessage()), sanitizePreview(responseBody, 300));
            throw AiProviderException.retryable(
                    "Failed to parse Groq response",
                    null,
                    providerName(),
                    model,
                    "/chat/completions",
                    ex
            );
        }
    }

    private AiProviderException mapGroqException(int status, RestClientResponseException ex) {
        String message = switch (status) {
            case 400 -> "Groq request failed with invalid request.";
            case 401, 403 -> "Groq authorization failed. Check API key/provider configuration.";
            case 429 -> "Groq quota exceeded.";
            case 500, 502, 503, 504 -> "Groq provider unavailable.";
            default -> status >= 500 ? "Groq provider unavailable." : "Groq request failed.";
        };
        boolean retryable = status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
        return retryable
                ? AiProviderException.retryable(message, status, providerName(), model, "/chat/completions", ex)
                : AiProviderException.fatal(message, status, providerName(), model, "/chat/completions", ex);
    }

    private boolean isTimeout(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("timed out");
    }

    private String correlationOrGenerated() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }

    private String sanitizePreview(String value, int maxLength) {
        String sanitized = sanitize(value);
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength);
    }

    private String safePreview(String value) {
        return sanitizePreview(value, 240);
    }

    private String previewStart(String value) {
        String sanitized = sanitize(value);
        if (sanitized.isBlank()) {
            return "";
        }
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(0, 300);
    }

    private String previewEnd(String value) {
        String sanitized = sanitize(value);
        if (sanitized.isBlank()) {
            return "";
        }
        return sanitized.length() <= 300 ? sanitized : sanitized.substring(sanitized.length() - 300);
    }

    private String formatTokenUsage(AiTokenUsage usage) {
        if (usage == null) {
            return "n/a";
        }
        return "prompt=" + usage.promptTokens()
                + ",completion=" + usage.completionTokens()
                + ",total=" + usage.totalTokens();
    }

    private AiTokenUsage tokenUsage(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return null;
        }
        long prompt = usage.path("prompt_tokens").asLong(0L);
        long completion = usage.path("completion_tokens").asLong(0L);
        long total = usage.path("total_tokens").asLong(prompt + completion);
        return new AiTokenUsage(prompt, completion, total, null);
    }

    private record ParsedGroqResponse(String text, int responseChars, String finishReason, AiTokenUsage tokenUsage) {
    }
}
