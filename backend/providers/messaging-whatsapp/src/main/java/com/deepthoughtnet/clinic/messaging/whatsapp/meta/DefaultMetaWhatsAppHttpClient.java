package com.deepthoughtnet.clinic.messaging.whatsapp.meta;

import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.whatsapp.CarePilotWhatsAppMessagingProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.util.StringUtils;

/**
 * Default HTTP implementation for Meta WhatsApp Cloud API calls.
 */
public class DefaultMetaWhatsAppHttpClient implements MetaWhatsAppHttpClient {
    private final HttpClient httpClient;

    public DefaultMetaWhatsAppHttpClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    DefaultMetaWhatsAppHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MetaWhatsAppHttpResponse sendText(MessageRequest request, CarePilotWhatsAppMessagingProperties properties) throws Exception {
        long timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 5000;
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.getApiUrl().trim()))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + properties.getAccessToken().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildPayload(request)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body() == null ? "" : response.body();
        String messageId = extractMessageId(responseBody);
        if (!StringUtils.hasText(messageId)) {
            messageId = request.executionId() == null ? UUID.randomUUID().toString() : request.executionId().toString();
        }
        return new MetaWhatsAppHttpResponse(response.statusCode(), responseBody, messageId);
    }

    private String buildPayload(MessageRequest request) {
        String to = escapeJson(request.recipient().address());
        String body = escapeJson(request.body());
        return "{"
                + "\"messaging_product\":\"whatsapp\","
                + "\"to\":\"" + to + "\","
                + "\"type\":\"text\","
                + "\"text\":{\"body\":\"" + body + "\"}"
                + "}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractMessageId(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        String quotedKey = "\"id\"";
        int keyIndex = payload.indexOf(quotedKey);
        if (keyIndex < 0) {
            return null;
        }
        int colon = payload.indexOf(':', keyIndex + quotedKey.length());
        if (colon < 0) {
            return null;
        }
        int firstQuote = payload.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return null;
        }
        int secondQuote = payload.indexOf('"', firstQuote + 1);
        if (secondQuote <= firstQuote + 1) {
            return null;
        }
        return payload.substring(firstQuote + 1, secondQuote);
    }
}

