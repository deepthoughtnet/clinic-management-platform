package com.deepthoughtnet.clinic.messaging.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls CarePilot SMS provider activation and baseline provider settings.
 */
@ConfigurationProperties(prefix = "carepilot.messaging.sms")
public class CarePilotSmsMessagingProperties {
    /**
     * Explicit enablement flag for SMS channel dispatch.
     */
    private boolean enabled = false;
    /**
     * Logical provider key (for example: disabled, twilio, msg91).
     */
    private String provider = "disabled";
    /**
     * Sender number or short code exposed to recipients.
     */
    private String fromNumber;
    /**
     * Vendor-neutral API endpoint used by the generic HTTP adapter.
     */
    private String apiUrl;
    /**
     * Optional placeholder for provider API key.
     */
    private String apiKey;
    /**
     * Optional sender identifier used by providers that support alpha sender IDs.
     */
    private String senderId;
    /**
     * Request timeout in milliseconds for HTTP based providers.
     */
    private long timeoutMs = 5000;
    /**
     * Optional shared secret expected on inbound SMS provider webhook callbacks.
     */
    private String webhookSecret;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }
}
