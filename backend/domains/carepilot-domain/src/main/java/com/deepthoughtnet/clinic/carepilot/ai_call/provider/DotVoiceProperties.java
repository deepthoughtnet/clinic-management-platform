package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration for the real DotVoice outbound-call adapter. */
@ConfigurationProperties(prefix = "clinic.carepilot.voice.dotvoice")
public class DotVoiceProperties {
    private boolean enabled;
    private String apiKey;
    private String baseUrl;
    private String callsPath = "/v1/calls";
    private String fromNumber;
    private String trunk;
    private String callbackBaseUrl;
    private int timeoutMs = 10_000;
    private boolean streamEnabled;
    private String streamUrl;
    private int streamTimeoutMs = 30_000;
    private int mediaFrameDurationMs = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getCallsPath() { return callsPath; }
    public void setCallsPath(String callsPath) { this.callsPath = callsPath; }
    public String getFromNumber() { return fromNumber; }
    public void setFromNumber(String fromNumber) { this.fromNumber = fromNumber; }
    /** Backward-compatible accessor for older local configuration/tests. */
    public String getDid() { return fromNumber; }
    public void setDid(String did) { this.fromNumber = did; }
    public void setTrunk(String trunk) { this.trunk = trunk; }
    public String getTrunk() { return trunk; }
    public String getCallbackBaseUrl() { return callbackBaseUrl; }
    public void setCallbackBaseUrl(String callbackBaseUrl) { this.callbackBaseUrl = callbackBaseUrl; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public boolean isStreamEnabled() { return streamEnabled; }
    public void setStreamEnabled(boolean streamEnabled) { this.streamEnabled = streamEnabled; }
    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
    public int getStreamTimeoutMs() { return streamTimeoutMs; }
    public void setStreamTimeoutMs(int streamTimeoutMs) { this.streamTimeoutMs = streamTimeoutMs; }
    public int getMediaFrameDurationMs() { return mediaFrameDurationMs; }
    public void setMediaFrameDurationMs(int mediaFrameDurationMs) { this.mediaFrameDurationMs = mediaFrameDurationMs; }

    public boolean isConfigured() {
        return enabled && hasText(apiKey) && hasText(baseUrl) && hasText(fromNumber) && hasText(trunk)
                && timeoutMs > 0;
    }

    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
}
