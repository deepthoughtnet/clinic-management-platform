package com.deepthoughtnet.clinic.llm.sarvam;

import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
import com.deepthoughtnet.clinic.llm.spi.LlmClient;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component("sarvamLlmClient")
@ConditionalOnExpression("'${clinic.ai.enabled:false}' == 'true' && '${clinic.ai.sarvam.enabled:false}' == 'true' && '${clinic.ai.sarvam.api-key:}' != ''")
public class SarvamLlmClient implements LlmClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Double temperature;
    private final Integer maxTokens;
    private final int timeoutSeconds;

    @Autowired
    public SarvamLlmClient(ObjectMapper objectMapper, RestClient.Builder builder,
                           @Value("${clinic.ai.sarvam.base-url:https://api.sarvam.ai}") String baseUrl,
                           @Value("${clinic.ai.sarvam.api-key:}") String apiKey,
                           @Value("${clinic.ai.sarvam.model:sarvam-105b-conversations}") String model,
                           @Value("${clinic.ai.sarvam.temperature:0.1}") Double temperature,
                           @Value("${clinic.ai.sarvam.max-output-tokens:2048}") Integer maxTokens,
                           @Value("${clinic.ai.sarvam.timeout-seconds:60}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.max(1, timeoutSeconds) * 1000);
        factory.setReadTimeout(Math.max(1, timeoutSeconds) * 1000);
        this.restClient = builder.requestFactory(factory).build();
        this.objectMapper = objectMapper;
        this.baseUrl = trim(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    SarvamLlmClient(ObjectMapper objectMapper, RestClient restClient, String baseUrl,
                    String apiKey, String model, Double temperature, Integer maxTokens,
                    int timeoutSeconds) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = trim(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override public String providerName() { return "SARVAM"; }

    @Override public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
    }

    @Override public String availabilityDiagnostic() {
        return isAvailable() ? null : "Sarvam semantic provider is not configured";
    }

    @Override public LlmResponse generate(LlmRequest request) {
        if (request == null || request.userPrompt() == null || request.userPrompt().isBlank()) {
            throw new IllegalArgumentException("userPrompt is required");
        }
        if (!isAvailable()) {
            throw AiProviderException.fatal(availabilityDiagnostic(), null, providerName(), model, "/v1/chat/completions", null);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.modelOverride() == null || request.modelOverride().isBlank() ? model : request.modelOverride());
        payload.put("messages", request.systemPrompt() == null || request.systemPrompt().isBlank()
                ? List.of(Map.of("role", "user", "content", request.userPrompt()))
                : List.of(Map.of("role", "system", "content", request.systemPrompt()), Map.of("role", "user", "content", request.userPrompt())));
        payload.put("temperature", request.temperature() == null ? temperature : request.temperature());
        payload.put("max_tokens", request.maxOutputTokens() == null ? maxTokens : request.maxOutputTokens());
        if (request.structuredOutputSchema() != null) {
            payload.put("response_format", Map.of("type", "json_schema", "json_schema", Map.of(
                    "name", "aiva_v2_conversation_decision", "strict", true,
                    "schema", SarvamResponseSchemaAdapter.adapt(request.structuredOutputSchema()))));
        } else if (request.strictJsonMode()) {
            payload.put("response_format", Map.of("type", "json_object"));
        }
        try {
            String body = restClient.post().uri(baseUrl + "/v1/chat/completions")
                    .header("api-subscription-key", apiKey).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).body(payload).retrieve().body(String.class);
            SarvamResponseAdapter.Parsed parsed = SarvamResponseAdapter.parse(objectMapper, body, providerName(), model);
            return new LlmResponse(providerName(), parsed.model(), parsed.text(), parsed.tokenUsage(), parsed.finishReason());
        } catch (RestClientResponseException ex) {
            int status = ex.getRawStatusCode();
            boolean retryable = status == 408 || status == 409 || status == 429 || status >= 500;
            throw (retryable ? AiProviderException.retryable("Sarvam request failed", status, providerName(), model, "/v1/chat/completions", ex)
                    : AiProviderException.fatal("Sarvam request failed", status, providerName(), model, "/v1/chat/completions", ex));
        } catch (ResourceAccessException ex) {
            if (isTimeout(ex)) {
                throw AiProviderException.retryable("Sarvam request timed out", null, providerName(), model, "/v1/chat/completions", ex);
            }
            throw AiProviderException.retryable("Sarvam request failed", null, providerName(), model, "/v1/chat/completions", ex);
        } catch (AiProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AiProviderException.retryable("Failed to parse Sarvam response", null, providerName(), model, "/v1/chat/completions", ex);
        }
    }

    private boolean isTimeout(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) if (current instanceof SocketTimeoutException) return true;
        return ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timed out");
    }

    private String trim(String value) { return value == null ? "" : value.trim().replaceAll("/+$", ""); }
}
