package com.deepthoughtnet.clinic.messaging.sms;

import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Default network transport for the generic HTTP SMS adapter.
 */
public class DefaultGenericHttpSmsClient implements GenericHttpSmsClient {
    private final HttpClient httpClient;

    public DefaultGenericHttpSmsClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    DefaultGenericHttpSmsClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public GenericHttpSmsResponse send(MessageRequest request, CarePilotSmsMessagingProperties properties) throws Exception {
        long timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 5000;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getApiUrl().trim()))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(request, properties)));
        if (StringUtils.hasText(properties.getApiKey())) {
            builder.header("Authorization", "Bearer " + properties.getApiKey().trim());
            builder.header("X-API-KEY", properties.getApiKey().trim());
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body() == null ? "" : response.body();
        String providerMessageId = extractMessageId(responseBody);
        if (!StringUtils.hasText(providerMessageId)) {
            providerMessageId = request.executionId() == null ? UUID.randomUUID().toString() : request.executionId().toString();
        }
        return new GenericHttpSmsResponse(response.statusCode(), responseBody, providerMessageId);
    }

    private String buildPayload(MessageRequest request, CarePilotSmsMessagingProperties properties) {
        String recipient = escapeJson(request.recipient().address());
        String body = escapeJson(request.body());
        String from = escapeJson(properties.getFromNumber());
        String senderId = escapeJson(properties.getSenderId());
        return "{"
                + "\"recipient\":\"" + recipient + "\","
                + "\"to\":\"" + recipient + "\","
                + "\"message\":\"" + body + "\","
                + "\"body\":\"" + body + "\","
                + "\"from\":\"" + from + "\","
                + "\"senderId\":\"" + senderId + "\""
                + "}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Extracts common message identifier fields from a provider JSON-ish payload.
     */
    private String extractMessageId(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        String[] keys = {"messageId", "message_id", "id", "sid"};
        for (String key : keys) {
            String direct = readJsonStringField(responseBody, key);
            if (StringUtils.hasText(direct)) {
                return direct;
            }
        }
        return null;
    }

    private String readJsonStringField(String payload, String key) {
        String quotedKey = "\"" + key + "\"";
        int keyIndex = payload.indexOf(quotedKey);
        if (keyIndex < 0) {
            return null;
        }
        int colon = payload.indexOf(':', keyIndex + quotedKey.length());
        if (colon < 0) {
            return null;
        }
        int valueStart = colon + 1;
        while (valueStart < payload.length() && Character.isWhitespace(payload.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= payload.length()) {
            return null;
        }
        if (payload.charAt(valueStart) == '"') {
            int end = payload.indexOf('"', valueStart + 1);
            if (end > valueStart + 1) {
                return payload.substring(valueStart + 1, end);
            }
            return null;
        }
        int end = valueStart;
        while (end < payload.length()) {
            char ch = payload.charAt(end);
            if (ch == ',' || ch == '}' || Character.isWhitespace(ch)) {
                break;
            }
            end++;
        }
        return end > valueStart ? payload.substring(valueStart, end) : null;
    }
}

