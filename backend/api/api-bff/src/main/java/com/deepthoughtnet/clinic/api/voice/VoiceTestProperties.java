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
    private final Vad vad = new Vad();
    private final Live live = new Live();
    private final Debug debug = new Debug();

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

    public Vad getVad() {
        return vad;
    }

    public Live getLive() {
        return live;
    }

    public Debug getDebug() {
        return debug;
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
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 180000;

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

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
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
        private String englishVoice = "en_US-lessac-medium";
        private String hindiVoice;
        private boolean allowFallbackVoice = true;
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 120000;

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

        public String getEnglishVoice() {
            return englishVoice;
        }

        public void setEnglishVoice(String englishVoice) {
            this.englishVoice = englishVoice;
        }

        public String getHindiVoice() {
            return hindiVoice;
        }

        public void setHindiVoice(String hindiVoice) {
            this.hindiVoice = hindiVoice;
        }

        public boolean isAllowFallbackVoice() {
            return allowFallbackVoice;
        }

        public void setAllowFallbackVoice(boolean allowFallbackVoice) {
            this.allowFallbackVoice = allowFallbackVoice;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
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
        private int maxOutputTokens = 1024;
        private int maxHistoryTurns = 4;
        private int maxHistoryChars = 900;
        private int maxAnswerWords = 35;

        public List<String> getProviderOrder() {
            return providerOrder;
        }

        public void setProviderOrder(List<String> providerOrder) {
            this.providerOrder = providerOrder;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public int getMaxHistoryTurns() {
            return maxHistoryTurns;
        }

        public void setMaxHistoryTurns(int maxHistoryTurns) {
            this.maxHistoryTurns = maxHistoryTurns;
        }

        public int getMaxHistoryChars() {
            return maxHistoryChars;
        }

        public void setMaxHistoryChars(int maxHistoryChars) {
            this.maxHistoryChars = maxHistoryChars;
        }

        public int getMaxAnswerWords() {
            return maxAnswerWords;
        }

        public void setMaxAnswerWords(int maxAnswerWords) {
            this.maxAnswerWords = maxAnswerWords;
        }
    }

    public static class Vad {
        private boolean enabled = true;
        private String provider = "noop";
        private double speechStartThreshold = 0.03;
        private double speechEndThreshold = 0.015;
        private int minSpeechMs = 300;
        private int silenceTimeoutMs = 1500;
        private int maxUtteranceMs = 30000;

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

        public double getSpeechStartThreshold() {
            return speechStartThreshold;
        }

        public void setSpeechStartThreshold(double speechStartThreshold) {
            this.speechStartThreshold = speechStartThreshold;
        }

        public double getSpeechEndThreshold() {
            return speechEndThreshold;
        }

        public void setSpeechEndThreshold(double speechEndThreshold) {
            this.speechEndThreshold = speechEndThreshold;
        }

        public int getMinSpeechMs() {
            return minSpeechMs;
        }

        public void setMinSpeechMs(int minSpeechMs) {
            this.minSpeechMs = minSpeechMs;
        }

        public int getSilenceTimeoutMs() {
            return silenceTimeoutMs;
        }

        public void setSilenceTimeoutMs(int silenceTimeoutMs) {
            this.silenceTimeoutMs = silenceTimeoutMs;
        }

        public int getMaxUtteranceMs() {
            return maxUtteranceMs;
        }

        public void setMaxUtteranceMs(int maxUtteranceMs) {
            this.maxUtteranceMs = maxUtteranceMs;
        }
    }

    public static class Debug {
        private boolean saveAudio = false;
        private String audioDir = "/tmp/clinic-voice-debug";

        public boolean isSaveAudio() {
            return saveAudio;
        }

        public void setSaveAudio(boolean saveAudio) {
            this.saveAudio = saveAudio;
        }

        public String getAudioDir() {
            return audioDir;
        }

        public void setAudioDir(String audioDir) {
            this.audioDir = audioDir;
        }
    }

    public static class Live {
        private int heartbeatIntervalMs = 15000;
        private int staleAfterMs = 45000;
        private int maxSessionDurationSeconds = 900;
        private int maxIdleSeconds = 120;
        private int maxTurnsPerSession = 20;
        private int maxAudioBytesPerTurn = 10 * 1024 * 1024;

        public int getHeartbeatIntervalMs() {
            return heartbeatIntervalMs;
        }

        public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
        }

        public int getStaleAfterMs() {
            return staleAfterMs;
        }

        public void setStaleAfterMs(int staleAfterMs) {
            this.staleAfterMs = staleAfterMs;
        }

        public int getMaxSessionDurationSeconds() {
            return maxSessionDurationSeconds;
        }

        public void setMaxSessionDurationSeconds(int maxSessionDurationSeconds) {
            this.maxSessionDurationSeconds = maxSessionDurationSeconds;
        }

        public int getMaxIdleSeconds() {
            return maxIdleSeconds;
        }

        public void setMaxIdleSeconds(int maxIdleSeconds) {
            this.maxIdleSeconds = maxIdleSeconds;
        }

        public int getMaxTurnsPerSession() {
            return maxTurnsPerSession;
        }

        public void setMaxTurnsPerSession(int maxTurnsPerSession) {
            this.maxTurnsPerSession = maxTurnsPerSession;
        }

        public int getMaxAudioBytesPerTurn() {
            return maxAudioBytesPerTurn;
        }

        public void setMaxAudioBytesPerTurn(int maxAudioBytesPerTurn) {
            this.maxAudioBytesPerTurn = maxAudioBytesPerTurn;
        }
    }
}
