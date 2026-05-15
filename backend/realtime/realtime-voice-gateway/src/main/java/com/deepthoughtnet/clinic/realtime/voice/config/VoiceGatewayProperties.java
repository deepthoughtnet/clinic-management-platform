package com.deepthoughtnet.clinic.realtime.voice.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Grouped runtime configuration for realtime voice gateway components.
 */
@ConfigurationProperties(prefix = "voice")
@Validated
public class VoiceGatewayProperties {
    private final Stt stt = new Stt();
    private final Tts tts = new Tts();
    private final Audio audio = new Audio();
    private final Websocket websocket = new Websocket();
    private final Runtime runtime = new Runtime();

    public Stt getStt() { return stt; }
    public Tts getTts() { return tts; }
    public Audio getAudio() { return audio; }
    public Websocket getWebsocket() { return websocket; }
    public Runtime getRuntime() { return runtime; }

    public static class Stt {
        @NotBlank
        private String provider = "mock-stt";
        @NotBlank
        private String model = "base";
        @NotBlank
        private String language = "en";
        @NotBlank
        private String device = "cpu";
        private String endpoint = "http://127.0.0.1:8091";
        @Min(100)
        private int timeoutMs = 7000;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getDevice() { return device; }
        public void setDevice(String device) { this.device = device; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class Tts {
        @NotBlank
        private String provider = "mock-tts";
        @NotBlank
        private String voice = "en_US-lessac-medium";
        private String endpoint = "http://127.0.0.1:8091";
        @Min(100)
        private int timeoutMs = 7000;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getVoice() { return voice; }
        public void setVoice(String voice) { this.voice = voice; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class Audio {
        @NotBlank
        private String acceptedFormats = "pcm,wav";
        @Min(100)
        private int vadSilenceMs = 1200;
        @Min(10)
        private int minChunkBytes = 160;

        public String getAcceptedFormats() { return acceptedFormats; }
        public void setAcceptedFormats(String acceptedFormats) { this.acceptedFormats = acceptedFormats; }
        public int getVadSilenceMs() { return vadSilenceMs; }
        public void setVadSilenceMs(int vadSilenceMs) { this.vadSilenceMs = vadSilenceMs; }
        public int getMinChunkBytes() { return minChunkBytes; }
        public void setMinChunkBytes(int minChunkBytes) { this.minChunkBytes = minChunkBytes; }
    }

    public static class Websocket {
        @Min(5)
        private int heartbeatSeconds = 15;

        public int getHeartbeatSeconds() { return heartbeatSeconds; }
        public void setHeartbeatSeconds(int heartbeatSeconds) { this.heartbeatSeconds = heartbeatSeconds; }
    }

    public static class Runtime {
        @NotBlank
        private String summaryEndpoint = "http://127.0.0.1:8091/metrics/summary";

        public String getSummaryEndpoint() { return summaryEndpoint; }
        public void setSummaryEndpoint(String summaryEndpoint) { this.summaryEndpoint = summaryEndpoint; }
    }
}
