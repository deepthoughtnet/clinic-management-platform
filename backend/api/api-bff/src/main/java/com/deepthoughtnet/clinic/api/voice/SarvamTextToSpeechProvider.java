package com.deepthoughtnet.clinic.api.voice;

import com.deepthoughtnet.clinic.api.voice.spi.TextToSpeechProvider;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component("voiceTestSarvamTextToSpeechProvider")
public class SarvamTextToSpeechProvider implements TextToSpeechProvider {
    private static final Logger log = LoggerFactory.getLogger(SarvamTextToSpeechProvider.class);
    private static final String CONFIGURED_PROVIDER_KEY = "sarvam";
    private static final String OUTPUT_AUDIO_CODEC = "linear16";
    private static final String PCM_CONTENT_TYPE = "audio/L16";

    private final VoiceTestProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    public SarvamTextToSpeechProvider(VoiceTestProperties properties,
                                      RestTemplateBuilder builder,
                                      ObjectMapper objectMapper) {
        this(properties, builder, objectMapper, new StandardEnvironment());
    }

    @Autowired
    SarvamTextToSpeechProvider(VoiceTestProperties properties,
                               RestTemplateBuilder builder,
                               ObjectMapper objectMapper,
                               Environment environment) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(properties.getSarvam().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getSarvam().getReadTimeoutMs()))
                .build();
        log.info("VOICE_PROVIDER_TRACE provider=sarvam type=TTS enabled={} ttsEnabled={} apiKeyConfigured={} baseUrl={} ttsPath={} defaultLanguage={} responseLanguage={} connectTimeoutMs={} readTimeoutMs={}",
                properties.getSarvam().isEnabled(),
                properties.getSarvam().isTtsEnabled(),
                StringUtils.hasText(properties.getSarvam().getApiKey()),
                sanitizedBaseUrl(),
                properties.getSarvam().getTtsPath(),
                properties.getDefaultLanguage(),
                properties.getResponseLanguage(),
                properties.getSarvam().getConnectTimeoutMs(),
                properties.getSarvam().getReadTimeoutMs());
    }

    @Override
    public String providerName() {
        return CONFIGURED_PROVIDER_KEY;
    }

    @Override
    public boolean isReady() {
        return properties.getSarvam().isEnabled()
                && properties.getSarvam().isTtsEnabled()
                && StringUtils.hasText(properties.getSarvam().getApiKey())
                && StringUtils.hasText(properties.getSarvam().getBaseUrl())
                && StringUtils.hasText(properties.getSarvam().getTtsPath());
    }

    @Override
    public VoiceSynthesisResult synthesize(VoiceSynthesisRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("Sarvam TTS is not configured.");
        }
        String language = resolveLanguage(request.language());
        String speaker = resolveSpeaker();
        String model = properties.getSarvam().getTtsModel();
        int sampleRate = properties.getSarvam().getTtsSampleRate();
        double pace = properties.getSarvam().getTtsPace();
        String endpoint = endpointUrl(properties.getSarvam().getTtsPath());
        Instant started = Instant.now();
        log.info("VOICE_TTS_SARVAM_REQUEST model={} speaker={} targetLanguage={} pace={} requestedSampleRate={} transport=REST textChars={}",
                model, speaker, language, pace, sampleRate, request.text() == null ? 0 : request.text().length());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(new MediaType("audio", "*"), MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            String apiKey = properties.getSarvam().getApiKey().trim();
            headers.setBearerAuth(apiKey);
            headers.set("x-api-key", apiKey);

            Map<String, Object> payload = Map.of(
                    "text", request.text(),
                    "language_code", language,
                    "model", model,
                    "speaker", speaker,
                    "pace", pace,
                    "speech_sample_rate", sampleRate,
                    "output_audio_codec", OUTPUT_AUDIO_CODEC
            );

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    byte[].class
            );
            VoiceSynthesisResult result = toResult(response);
            log.info("VOICE_TTS_SARVAM_SUCCESS model={} speaker={} requestedSampleRate={} actualSampleRate={} outputFormat={} audioBytes={} latencyMs={}",
                    model, speaker, sampleRate, audioSampleRate(result, sampleRate), OUTPUT_AUDIO_CODEC,
                    result.audioBytes() == null ? 0 : result.audioBytes().length,
                    Duration.between(started, Instant.now()).toMillis());
            return result;
        } catch (HttpStatusCodeException ex) {
            log.warn("VOICE_TTS_SARVAM_FAILURE status={} errorCategory={} latencyMs={}",
                    ex.getStatusCode().value(),
                    "HTTP_STATUS",
                    Duration.between(started, Instant.now()).toMillis());
            throw new IllegalStateException("Sarvam synthesis failed.", ex);
        } catch (RestClientException ex) {
            log.warn("VOICE_TTS_SARVAM_FAILURE status=unavailable errorCategory={} latencyMs={}",
                    ex.getClass().getSimpleName(), Duration.between(started, Instant.now()).toMillis());
            throw new IllegalStateException("Sarvam synthesis failed.", ex);
        } catch (Exception ex) {
            log.warn("VOICE_TTS_SARVAM_FAILURE status=unavailable errorCategory={} latencyMs={}",
                    ex.getClass().getSimpleName(), Duration.between(started, Instant.now()).toMillis());
            throw new IllegalStateException("Sarvam synthesis failed.", ex);
        }
    }

    private int audioSampleRate(VoiceSynthesisResult result, int requestedSampleRate) {
        byte[] audio = result == null ? null : result.audioBytes();
        if (audio != null && audio.length >= 28 && audio[0] == 'R' && audio[1] == 'I'
                && audio[2] == 'F' && audio[3] == 'F') {
            return ByteBuffer.wrap(audio, 24, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        }
        return result != null && result.contentType() != null
                && (result.contentType().toLowerCase(Locale.ROOT).contains("pcm")
                || result.contentType().toLowerCase(Locale.ROOT).contains("l16"))
                ? requestedSampleRate : 0;
    }

    private VoiceSynthesisResult toResult(ResponseEntity<byte[]> response) {
        byte[] body = response.getBody();
        MediaType contentType = response.getHeaders().getContentType();
        log.info("voice.tts.response provider=sarvam contentType={} sizeBytes={}",
                contentType == null ? null : contentType.toString(),
                body == null ? 0 : body.length);
        if (body != null && body.length > 0 && isLinear16ContentType(contentType)) {
            return linear16Result(body);
        }
        if (body != null && body.length > 0 && isAudioContentType(contentType)) {
            throw new IllegalStateException("Sarvam returned an unexpected audio codec.");
        }
        String responseText = body == null ? null : new String(body, StandardCharsets.UTF_8).trim();
        if (StringUtils.hasText(responseText)) {
            if (responseText.startsWith("{") || responseText.startsWith("[")) {
                try {
                    JsonNode root = objectMapper.readTree(responseText);
                    logJsonShape(root);
                    AudioPayload payload = findAudioPayload(root, "$");
                    if (payload != null && payload.audioBytes() != null && payload.audioBytes().length > 0) {
                        log.info("voice.tts.json_audio_field provider=sarvam fieldPath={} decodedBytes={}",
                                payload.fieldPath(),
                                payload.audioBytes().length);
                        return linear16Result(payload.audioBytes());
                    }
                } catch (Exception ignored) {
                    // fall back to text interpretation below
                }
            } else {
                byte[] decoded = decodeBase64(responseText);
                if (decoded != null && decoded.length > 0) {
                    return linear16Result(decoded);
                }
            }
        }
        throw new IllegalStateException("Sarvam returned an empty audio response.");
    }

    private VoiceSynthesisResult linear16Result(byte[] audio) {
        if (audio == null || audio.length == 0 || audio.length % 2 != 0) {
            throw new IllegalStateException("Sarvam returned malformed Linear16 audio.");
        }
        return new VoiceSynthesisResult(audio, PCM_CONTENT_TYPE, providerName(), null);
    }

    private AudioPayload findAudioPayload(JsonNode node, String path) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isValueNode()) {
            if (node.isTextual()) {
                String text = node.asText(null);
                if (StringUtils.hasText(text)) {
                    byte[] decoded = decodeBase64(text);
                    if (decoded != null && decoded.length > 0) {
                        return new AudioPayload(decoded, path);
                    }
                }
            }
            return null;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode item : node) {
                AudioPayload payload = findAudioPayload(item, path + "[" + index + "]");
                if (payload != null) {
                    return payload;
                }
                index++;
            }
            return null;
        }
        if (node.isObject()) {
            for (String field : AUDIO_JSON_FIELDS) {
                if (node.has(field)) {
                    JsonNode child = node.get(field);
                    if (URL_FIELDS.contains(field) && child != null && child.isValueNode() && child.isTextual()) {
                        log.info("voice.tts.json_unsupported_url provider=sarvam fieldPath={} urlPresent=true", path + "." + field);
                        continue;
                    }
                    AudioPayload payload = findAudioPayload(child, path + "." + field);
                    if (payload != null) {
                        return payload;
                    }
                }
            }
            return null;
        }
        return null;
    }

    private void logJsonShape(JsonNode root) {
        if (root == null) {
            return;
        }
        if (root.isArray()) {
            List<String> firstItemFields = new ArrayList<>();
            Iterator<JsonNode> iterator = root.elements();
            if (iterator.hasNext()) {
                JsonNode first = iterator.next();
                if (first != null && first.isObject()) {
                    first.fieldNames().forEachRemaining(firstItemFields::add);
                }
            }
            log.info("voice.tts.json_shape provider=sarvam topLevelType=array topLevelSize={} firstItemFields={}",
                    root.size(),
                    firstItemFields);
            return;
        }
        if (root.isObject()) {
            List<String> fields = new ArrayList<>();
            root.fieldNames().forEachRemaining(fields::add);
            log.info("voice.tts.json_shape provider=sarvam topLevelType=object topLevelFields={}", fields);
        }
    }

    private boolean isAudioContentType(MediaType contentType) {
        return contentType != null
                && StringUtils.hasText(contentType.toString())
                && contentType.getType() != null
                && "audio".equalsIgnoreCase(contentType.getType());
    }

    private boolean isLinear16ContentType(MediaType contentType) {
        if (contentType == null) return false;
        String value = contentType.toString().toLowerCase(Locale.ROOT);
        return value.startsWith("audio/l16") || value.startsWith("audio/pcm")
                || value.startsWith("application/octet-stream");
    }

    private byte[] decodeBase64(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(value.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String resolveLanguage(String requestLanguage) {
        if (StringUtils.hasText(requestLanguage)) {
            String normalized = requestLanguage.trim();
            if (!"auto".equalsIgnoreCase(normalized) && !"auto-detect".equalsIgnoreCase(normalized)) {
                if ("hi".equalsIgnoreCase(normalized) || "hi-in".equalsIgnoreCase(normalized)) {
                    return "hi-IN";
                }
                if (normalized.toLowerCase(Locale.ROOT).startsWith("en")) {
                    return "en-IN";
                }
                return normalized;
            }
        }
        String configuredLanguage = properties.getSarvam().getTtsLanguageCode();
        return StringUtils.hasText(configuredLanguage) ? configuredLanguage : "hi-IN";
    }

    private String resolveSpeaker() {
        String speaker = propertyOrEnv("SARVAM_TTS_SPEAKER");
        if (StringUtils.hasText(speaker)) {
            return speaker.trim();
        }
        // The typed Sarvam property owns this provider. Keep the legacy shared
        // voice setting as a fallback only, so it cannot override this default.
        speaker = properties.getSarvam().getTtsSpeaker();
        if (StringUtils.hasText(speaker)) {
            return speaker.trim();
        }
        speaker = propertyOrEnv("AIVA_DEFAULT_VOICE");
        return StringUtils.hasText(speaker) ? speaker.trim() : "aparna_hi_customer";
    }

    private String propertyOrEnv(String key) {
        return environment == null ? null : environment.getProperty(key);
    }

    private String extractReason(HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        if (StringUtils.hasText(body)) {
            return summarize(body);
        }
        return ex.getStatusText();
    }

    private String endpointUrl(String path) {
        String baseUrl = properties.getSarvam().getBaseUrl().replaceAll("/+$", "");
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return baseUrl + normalizedPath;
    }

    private String sanitizedBaseUrl() {
        return StringUtils.hasText(properties.getSarvam().getBaseUrl())
                ? properties.getSarvam().getBaseUrl().replaceAll("/+$", "")
                : "";
    }

    private String configuredResponseLanguage() {
        if (StringUtils.hasText(properties.getResponseLanguage())) {
            return properties.getResponseLanguage().trim();
        }
        if (StringUtils.hasText(properties.getSarvam().getResponseLanguage())) {
            return properties.getSarvam().getResponseLanguage().trim();
        }
        if (StringUtils.hasText(properties.getSarvam().getDefaultLanguage())) {
            return properties.getSarvam().getDefaultLanguage().trim();
        }
        if (StringUtils.hasText(properties.getDefaultLanguage())) {
            return properties.getDefaultLanguage().trim();
        }
        return null;
    }

    private String summarize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String compact = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "...";
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return StringUtils.hasText(message) ? message : throwable.getClass().getSimpleName();
    }

    private static final Set<String> AUDIO_JSON_FIELDS = Set.of(
            "audios",
            "audio",
            "audioContent",
            "audio_content",
            "base64_audio",
            "data",
            "result",
            "output",
            "content",
            "audioBytes",
            "audioBase64",
            "audio_base64",
            "audioUrl",
            "audio_url",
            "url"
    );

    private static final Set<String> URL_FIELDS = Set.of(
            "audioUrl",
            "audio_url",
            "url"
    );

    private record AudioPayload(byte[] audioBytes, String fieldPath) {
        private AudioPayload {
            audioBytes = Objects.requireNonNull(audioBytes, "audioBytes");
        }
    }
}
