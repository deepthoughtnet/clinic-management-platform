package com.deepthoughtnet.clinic.messaging.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls CarePilot WhatsApp provider activation and baseline provider settings.
 */
@ConfigurationProperties(prefix = "carepilot.messaging.whatsapp")
public class CarePilotWhatsAppMessagingProperties {
    /**
     * Explicit enablement flag for WhatsApp channel dispatch.
     */
    private boolean enabled = false;
    /**
     * Logical provider key (for example: disabled, meta, gupshup).
     */
    private String provider = "disabled";
    /**
     * Vendor API URL endpoint for message dispatch.
     */
    private String apiUrl;
    /**
     * Access token for the external provider.
     */
    private String accessToken;
    /**
     * Meta WhatsApp phone-number-id used for Cloud API sends.
     */
    private String phoneNumberId;
    /**
     * Sender number mapped to the business account.
     */
    private String fromNumber;
    /**
     * Optional business account identifier required by some providers.
     */
    private String businessAccountId;
    /**
     * Request timeout in milliseconds for provider HTTP calls.
     */
    private long timeoutMs = 5000;
    /**
     * Meta webhook verification token for GET challenge.
     */
    private String webhookVerifyToken;
    /**
     * Optional Meta app secret for webhook signature validation.
     */
    private String appSecret;

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

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getBusinessAccountId() {
        return businessAccountId;
    }

    public void setBusinessAccountId(String businessAccountId) {
        this.businessAccountId = businessAccountId;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public void setWebhookVerifyToken(String webhookVerifyToken) {
        this.webhookVerifyToken = webhookVerifyToken;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }
}
