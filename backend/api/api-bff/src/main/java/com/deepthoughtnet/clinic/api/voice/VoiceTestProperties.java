package com.deepthoughtnet.clinic.api.voice;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "voice")
public class VoiceTestProperties {
    private boolean enabled = true;
    private final Stt stt = new Stt();
    private final Tts tts = new Tts();
    private final Llm llm = new Llm();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Stt getStt() {
        return stt;
    }

    public Tts getTts() {
        return tts;
    }

    public Llm getLlm() {
        return llm;
    }

    public static class Stt {
        private List<String> providerOrder = List.of("faster-whisper", "mock");
        private final FasterWhisper fasterWhisper = new FasterWhisper();
        private final Deepgram deepgram = new Deepgram();

        public List<String> getProviderOrder() {
            return providerOrder;
        }

        public void setProviderOrder(List<String> providerOrder) {
            this.providerOrder = providerOrder;
        }

        public Deepgram getDeepgram() {
            return deepgram;
        }

        public FasterWhisper getFasterWhisper() {
            return fasterWhisper;
        }
    }

    public static class FasterWhisper {
        private String baseUrl = "http://faster-whisper:8000";
        private String model = "base";
        private String language = "en";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    public static class Deepgram {
        private String apiKey;
        private String model = "nova-2";
        private String baseUrl = "https://api.deepgram.com/v1/listen";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Tts {
        private List<String> providerOrder = List.of("piper", "mock");
        private final Piper piper = new Piper();
        private final ElevenLabs elevenlabs = new ElevenLabs();

        public List<String> getProviderOrder() {
            return providerOrder;
        }

        public void setProviderOrder(List<String> providerOrder) {
            this.providerOrder = providerOrder;
        }

        public ElevenLabs getElevenlabs() {
            return elevenlabs;
        }

        public Piper getPiper() {
            return piper;
        }
    }

    public static class Piper {
        private String baseUrl = "http://piper-tts:8001";
        private String voice = "en_US-lessac-medium";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getVoice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }
    }

    public static class ElevenLabs {
        private String apiKey;
        private String voiceId;
        private String model = "eleven_multilingual_v2";
        private String baseUrl = "https://api.elevenlabs.io/v1/text-to-speech";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getVoiceId() {
            return voiceId;
        }

        public void setVoiceId(String voiceId) {
            this.voiceId = voiceId;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Llm {
        private List<String> providerOrder = List.of("gemini", "groq", "mock");

        public List<String> getProviderOrder() {
            return providerOrder;
        }

        public void setProviderOrder(List<String> providerOrder) {
            this.providerOrder = providerOrder;
        }
    }
}
