package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** Bounded DotVoice stream session used by platform reminder tests. */
public class DefaultDotVoiceMediaStreamClient implements DotVoiceMediaStreamClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultDotVoiceMediaStreamClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DotVoiceProperties properties;
    private final AtomicReference<PreparedStream> prepared = new AtomicReference<>();
    private final ConcurrentHashMap<String, PendingStart> pendingStarts = new ConcurrentHashMap<>();

    public DefaultDotVoiceMediaStreamClient(DotVoiceProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper());
    }

    DefaultDotVoiceMediaStreamClient(DotVoiceProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isReady() {
        return properties != null && properties.isStreamEnabled()
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getStreamUrl())
                && properties.getStreamTimeoutMs() > 0;
    }

    @Override
    public boolean prepare() {
        closePrepared();
        if (!isReady()) return false;
        CompletableFuture<Boolean> ready = new CompletableFuture<>();
        AtomicReference<PlaybackContext> active = new AtomicReference<>();
        try {
            String endpoint = endpoint();
            URI uri = URI.create(endpoint);
            log.info("DOTVOICE_STREAM_CONNECTING urlHost={} path={} apiKeyPresent={}",
                    uri.getHost(), uri.getPath(), StringUtils.hasText(properties.getApiKey()));
            WebSocket socket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getStreamTimeoutMs()))
                    .buildAsync(uri, listener(ready, active))
                    .get(properties.getStreamTimeoutMs(), TimeUnit.MILLISECONDS);
            log.info("DOTVOICE_STREAM_CONNECTED");
            if (!ready.get(properties.getStreamTimeoutMs(), TimeUnit.MILLISECONDS)) {
                log.warn("DOTVOICE_STREAM_TIMEOUT callId={} waitedMs={}", null, properties.getStreamTimeoutMs());
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "ready-timeout");
                return false;
            }
            prepared.set(new PreparedStream(socket, active));
            return true;
        } catch (Exception ex) {
            Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                    ? ex.getCause() : ex;
            String category = cause instanceof StreamReadinessException readiness
                    ? readiness.category() : "STREAM_READY_TIMEOUT";
            log.warn("DOTVOICE_STREAM_READY_FAILED category={} reason={}", category, sanitize(cause.getMessage()));
            return false;
        }
    }

    @Override
    public PlaybackResult play(String providerCallId, List<byte[]> mediaFrames) {
        if (!StringUtils.hasText(providerCallId) || mediaFrames == null || mediaFrames.isEmpty()) {
            return new PlaybackResult(false, "PLAYBACK_FAILED", "AUDIO_FORMAT_FAILED", 0, null);
        }
        PreparedStream stream = prepared.getAndSet(null);
        if (stream == null) {
            return new PlaybackResult(false, "STREAM_NOT_READY", "STREAM_READY_NOT_RECEIVED", 0, null);
        }
        log.info("DOTVOICE_STREAM_REGISTER_CALL callId={}", providerCallId);
        CompletableFuture<PlaybackResult> completion = new CompletableFuture<>();
        PlaybackContext context = new PlaybackContext(providerCallId, mediaFrames, completion);
        stream.active.set(context);
        PendingStart buffered = pendingStarts.remove(providerCallId);
        if (buffered != null && System.currentTimeMillis() - buffered.createdAt() < properties.getStreamTimeoutMs()) {
            startPlayback(stream.socket(), context, buffered.node());
        }
        try {
            return completion.orTimeout(properties.getStreamTimeoutMs(), TimeUnit.MILLISECONDS).join();
        } catch (Exception ex) {
            log.warn("DOTVOICE_STREAM_TIMEOUT callId={} waitedMs={}", providerCallId, properties.getStreamTimeoutMs());
            return new PlaybackResult(false, "STREAM_START_TIMEOUT", "STREAM_START_TIMEOUT", context.sent.get(), null);
        } finally {
            stream.active().compareAndSet(context, null);
            try { stream.socket().sendClose(WebSocket.NORMAL_CLOSURE, "playback-complete"); } catch (RuntimeException ignored) { }
        }
    }

    @Override
    public void abort() {
        closePrepared();
    }

    private WebSocket.Listener listener(CompletableFuture<Boolean> ready, AtomicReference<PlaybackContext> active) {
        StringBuilder textBuffer = new StringBuilder();
        return new WebSocket.Listener() {
            @Override public void onOpen(WebSocket socket) {
                socket.request(1);
                WebSocket.Listener.super.onOpen(socket);
            }

            @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                try {
                    textBuffer.append(data);
                    if (!last) return WebSocket.Listener.super.onText(socket, data, last);
                    int payloadLength = textBuffer.length();
                    JsonNode node = objectMapper.readTree(textBuffer.toString());
                    textBuffer.setLength(0);
                    String event = resolveEvent(node);
                    String callId = text(node, "call_id", "callId");
                    log.info("DOTVOICE_STREAM_EVENT event={} callId={} keys={}", event, callId, safeKeys(node));
                    if ("ready".equalsIgnoreCase(event)) {
                        ready.complete(true);
                    } else if ("error".equalsIgnoreCase(event)) {
                        logProviderError(node, callId);
                        ready.completeExceptionally(new StreamReadinessException("STREAM_PROVIDER_REJECTED"));
                    } else if ("stream_start".equalsIgnoreCase(event)) {
                        PlaybackContext context = active.get();
                        if (context == null) {
                            if (StringUtils.hasText(callId)) pendingStarts.put(callId, new PendingStart(node, System.currentTimeMillis()));
                            pendingStarts.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().createdAt() > properties.getStreamTimeoutMs());
                        } else if (context.callId.equals(callId)) {
                            log.info("DOTVOICE_STREAM_MATCH callId={} matched=true", callId);
                            startPlayback(socket, context, node);
                        } else {
                            log.info("DOTVOICE_STREAM_MATCH callId={} matched=false", callId);
                        }
                    } else if ("stream_stop".equalsIgnoreCase(event) && active.get() != null
                            && active.get().callId.equals(callId)) {
                        PlaybackContext context = active.get();
                        context.completion.complete(new PlaybackResult(true, "PLAYBACK_COMPLETED", null, context.sent.get(), text(node, "cause")));
                    } else if (!isKnownEvent(event)) {
                        logUnknownEvent(node, event, callId, payloadLength);
                    }
                } catch (Exception ex) {
                    PlaybackContext context = active.get();
                    if (context != null) context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", "STREAM_DISCONNECTED", context.sent.get(), null));
                }
                socket.request(1);
                return WebSocket.Listener.super.onText(socket, data, last);
            }

            @Override public CompletionStage<?> onBinary(WebSocket socket, java.nio.ByteBuffer data, boolean last) {
                log.info("DOTVOICE_STREAM_FRAME type=BINARY bytes={} last={}", data == null ? 0 : data.remaining(), last);
                socket.request(1);
                return WebSocket.Listener.super.onBinary(socket, data, last);
            }

            @Override public CompletionStage<?> onPing(WebSocket socket, java.nio.ByteBuffer message) {
                log.info("DOTVOICE_STREAM_FRAME type=PING bytes={}", message == null ? 0 : message.remaining());
                socket.request(1);
                return WebSocket.Listener.super.onPing(socket, message);
            }

            @Override public CompletionStage<?> onPong(WebSocket socket, java.nio.ByteBuffer message) {
                log.info("DOTVOICE_STREAM_FRAME type=PONG bytes={}", message == null ? 0 : message.remaining());
                socket.request(1);
                return WebSocket.Listener.super.onPong(socket, message);
            }

            @Override public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
                log.info("DOTVOICE_STREAM_FRAME type=CLOSE statusCode={} reasonPresent={}", statusCode,
                        StringUtils.hasText(reason));
                if (!ready.isDone()) {
                    ready.completeExceptionally(new StreamReadinessException("STREAM_CLOSED_BEFORE_READY"));
                }
                return WebSocket.Listener.super.onClose(socket, statusCode, reason);
            }

            @Override public void onError(WebSocket socket, Throwable error) {
                ready.complete(false);
                PlaybackContext context = active.get();
                if (context != null) context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", "STREAM_DISCONNECTED", context.sent.get(), null));
            }
        };
    }

    private void startPlayback(WebSocket socket, PlaybackContext context, JsonNode node) {
        if (!context.started.compareAndSet(false, true)) return;
        String encoding = text(node, "encoding");
        int sampleRate = node != null && node.has("sample_rate") ? node.get("sample_rate").asInt() : 8_000;
        if (StringUtils.hasText(encoding) && !encoding.toLowerCase(java.util.Locale.ROOT).contains("mulaw")
                && !encoding.toLowerCase(java.util.Locale.ROOT).contains("mu-law")) {
            context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", "AUDIO_FORMAT_FAILED", 0, null));
            return;
        }
        if (sampleRate != 8_000) {
            context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", "AUDIO_FORMAT_FAILED", 0, null));
            return;
        }
        sendFrames(socket, context, properties.getMediaFrameDurationMs());
    }

    private void sendFrames(WebSocket socket, PlaybackContext context, int frameDurationMs) {
        CompletableFuture.runAsync(() -> {
            try {
                for (byte[] frame : context.frames) {
                    socket.sendText(objectMapper.writeValueAsString(Map.of("event", "media", "call_id", context.callId,
                            "audio", Base64.getEncoder().encodeToString(frame))), true).join();
                    context.sent.incrementAndGet();
                    if (frameDurationMs > 0) Thread.sleep(frameDurationMs);
                }
            } catch (Exception ex) {
                context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", "STREAM_DISCONNECTED", context.sent.get(), null));
            }
        });
    }

    private String endpoint() {
        String endpoint = properties.getStreamUrl().trim();
        String separator = endpoint.contains("?") ? "&" : "?";
        return endpoint + separator + "api_key=" + URLEncoder.encode(properties.getApiKey().trim(), StandardCharsets.UTF_8);
    }

    private void closePrepared() {
        PreparedStream stream = prepared.getAndSet(null);
        if (stream != null) try { stream.socket().sendClose(WebSocket.NORMAL_CLOSURE, "replaced"); } catch (RuntimeException ignored) { }
    }

    private List<String> safeKeys(JsonNode node) {
        if (node == null || !node.isObject()) return List.of();
        List<String> keys = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return List.copyOf(keys);
    }

    private void logUnknownEvent(JsonNode node, String event, String callId, int payloadLength) {
        log.info("DOTVOICE_STREAM_UNKNOWN_EVENT event={} keys={} callId={} digit={} key={} duration={} sequence={} "
                        + "timestamp={} payloadLength={}",
                event, safeKeys(node), callId, scalar(node, "digit"), scalar(node, "key"), scalar(node, "duration"),
                scalar(node, "sequence", "sequence_number"), scalar(node, "timestamp", "event_time"), payloadLength);
    }

    private void logProviderError(JsonNode node, String callId) {
        log.warn("DOTVOICE_STREAM_PROVIDER_ERROR event={} code={} message={} reason={} callId={} keys={}",
                "error", sanitize(scalar(node, "code")), sanitize(scalar(node, "message")), sanitize(scalar(node, "reason")),
                sanitize(callId), safeKeys(node));
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) return null;
        String sanitized = value.replaceAll("(?i)https?://\\S+|wss?://\\S+", "[redacted-url]")
                .replaceAll("(?i)(api[_-]?key|token|secret|authorization)\\s*[:=]\\s*\\S+", "$1=[redacted]")
                .replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.length() <= 160 ? sanitized : sanitized.substring(0, 160);
    }

    private String scalar(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && value.isValueNode()) {
                String text = value.asText();
                if (StringUtils.hasText(text) && text.length() <= 80) return text;
            }
        }
        return null;
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) return value.asText().trim();
        }
        return null;
    }

    private String resolveEvent(JsonNode node) {
        String event = text(node, "event", "type", "name");
        if (isKnownEvent(event)) return event;
        String message = text(node, "message");
        return isKnownEvent(message) ? message : event;
    }

    private boolean isKnownEvent(String value) {
        return value != null && switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "ready", "stream_start", "media", "stream_stop", "control" -> true;
            default -> false;
        };
    }

    private record PreparedStream(WebSocket socket, AtomicReference<PlaybackContext> active) {}
    private record PendingStart(JsonNode node, long createdAt) {}

    private static final class PlaybackContext {
        private final String callId;
        private final List<byte[]> frames;
        private final CompletableFuture<PlaybackResult> completion;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicInteger sent = new AtomicInteger();
        private PlaybackContext(String callId, List<byte[]> frames, CompletableFuture<PlaybackResult> completion) {
            this.callId = callId; this.frames = frames; this.completion = completion;
        }
    }

    private static final class StreamReadinessException extends RuntimeException {
        private final String category;
        private StreamReadinessException(String category) {
            super(category);
            this.category = category;
        }
        private String category() { return category; }
    }
}
