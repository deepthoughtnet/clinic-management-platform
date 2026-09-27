package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal DotVoice REST transport. Secrets and endpoint remain configuration-owned. */
public class DefaultDotVoiceHttpClient implements DotVoiceHttpClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DefaultDotVoiceHttpClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper());
    }

    DefaultDotVoiceHttpClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public DotVoiceHttpResponse createCall(DotVoiceProperties properties, String destination, String script) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from_number", properties.getFromNumber().trim());
            payload.put("to", destination);
            payload.put("trunk", properties.getTrunk().trim());
            String base = properties.getBaseUrl().trim();
            String path = properties.getCallsPath() == null ? "/v1/calls" : properties.getCallsPath().trim();
            String endpoint = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            endpoint += path.startsWith("/") ? path : "/" + path;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("X-API-Key", properties.getApiKey().trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new DotVoiceHttpResponse(response.statusCode(), response.body());
        } catch (Exception ex) {
            return new DotVoiceHttpResponse(599, null);
        }
    }
}
