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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Semaphore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/** Bounded DotVoice stream session used by platform reminder tests. */
public class DefaultDotVoiceMediaStreamClient implements DotVoiceMediaStreamClient {
    private static final Logger log = LoggerFactory.getLogger(DefaultDotVoiceMediaStreamClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DotVoiceProperties properties;
    private final DotVoiceHttpClient dotVoiceHttpClient;
    private final List<DotVoiceInboundMediaHandler> inboundHandlers;
    private final AtomicReference<PreparedStream> prepared = new AtomicReference<>();
    private final ConcurrentHashMap<String, PendingStart> pendingStarts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PreparedStream> activeStreams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> inboundCallIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> inboundAnswered = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> playbackLocks = new ConcurrentHashMap<>();
    private final ScheduledThreadPoolExecutor playbackWriter = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "dotvoice-playback-writer-1");
        thread.setDaemon(true);
        return thread;
    });
    private final Semaphore playbackAdmission;
    private final Semaphore controlWriteAdmission;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "dotvoice-inbound-reconnect");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean persistentListenerRunning = new AtomicBoolean();
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicReference<ScheduledFuture<?>> reconnectTask = new AtomicReference<>();
    private volatile String listenerState = "DISCONNECTED";

    public DefaultDotVoiceMediaStreamClient(DotVoiceProperties properties) {
        this(properties, null, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper(), List.of());
    }

    DefaultDotVoiceMediaStreamClient(DotVoiceProperties properties, HttpClient httpClient, ObjectMapper objectMapper) {
        this(properties, null, httpClient, objectMapper, List.of());
    }

    DefaultDotVoiceMediaStreamClient(DotVoiceProperties properties, HttpClient httpClient, ObjectMapper objectMapper,
                                     List<DotVoiceInboundMediaHandler> inboundHandlers) {
        this(properties, null, httpClient, objectMapper, inboundHandlers);
    }

    DefaultDotVoiceMediaStreamClient(DotVoiceProperties properties, DotVoiceHttpClient dotVoiceHttpClient,
                                     HttpClient httpClient, ObjectMapper objectMapper,
                                     List<DotVoiceInboundMediaHandler> inboundHandlers) {
        this.properties = properties;
        this.dotVoiceHttpClient = dotVoiceHttpClient;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.inboundHandlers = inboundHandlers == null ? List.of() : List.copyOf(inboundHandlers);
        playbackWriter.setRemoveOnCancelPolicy(true);
        playbackAdmission = new Semaphore(Math.max(1, properties == null ? 32 : properties.getPlaybackQueueCapacity()));
        controlWriteAdmission = new Semaphore(Math.max(1, properties == null ? 32 : properties.getPlaybackQueueCapacity()));
    }

    @Override
    public boolean isReady() {
        return properties != null && properties.isStreamEnabled()
                && StringUtils.hasText(properties.getApiKey())
                && StringUtils.hasText(properties.getStreamUrl())
                && properties.getStreamTimeoutMs() > 0;
    }

    @Override
    public void onPlaybackPreparationFailed(String providerCallId) {
        inboundHandlers.forEach(handler -> handler.onPlaybackPreparationFailed(providerCallId));
    }

    @Override
    public boolean isConnected() {
        PreparedStream preparedStream = prepared.get();
        if (preparedStream != null && !preparedStream.socket().isInputClosed() && !preparedStream.socket().isOutputClosed()) return true;
        return activeStreams.values().stream()
                .anyMatch(stream -> !stream.socket().isInputClosed() && !stream.socket().isOutputClosed());
    }

    @Override
    public String connectionState() { return listenerState; }

    @Override
    public void startPersistentListener() {
        if (!persistentListenerRunning.compareAndSet(false, true)) return;
        log.info("DOTVOICE_INBOUND_LISTENER_STARTING");
        if (!isReady()) {
            listenerState = "FAILED";
            log.warn("DOTVOICE_INBOUND_LISTENER_FAILED reason=configuration_not_ready");
            return;
        }
        connectManaged(0);
    }

    @Override
    public void stopPersistentListener() {
        if (!persistentListenerRunning.compareAndSet(true, false)) return;
        ScheduledFuture<?> task = reconnectTask.getAndSet(null);
        if (task != null) task.cancel(false);
        reconnectScheduler.shutdownNow();
        listenerState = "DISCONNECTED";
        closeAllStreams();
    }

    @PreDestroy
    public void shutdownPlaybackWriter() {
        playbackWriter.shutdownNow();
        reconnectScheduler.shutdownNow();
    }

    @Override
    public boolean prepare() {
        if (isConnected()) return true;
        closePrepared();
        if (!isReady()) return false;
        listenerState = "CONNECTING";
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
            listenerState = "CONNECTED";
            return true;
        } catch (Exception ex) {
            Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                    ? ex.getCause() : ex;
            String category = cause instanceof StreamReadinessException readiness
                    ? readiness.category() : "STREAM_READY_TIMEOUT";
            log.warn("DOTVOICE_STREAM_READY_FAILED category={} reason={}", category, sanitize(cause.getMessage()));
            listenerState = "FAILED";
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
        activeStreams.put(providerCallId, stream);
        PendingStart buffered = pendingStarts.remove(providerCallId);
        if (buffered != null && System.currentTimeMillis() - buffered.createdAt() < properties.getStreamTimeoutMs()) {
            inboundHandlers.forEach(handler -> handler.onStreamStartCorrelation(providerCallId, true, true));
            startPlayback(stream, context, buffered.node());
        }
        try {
            return completion.orTimeout(properties.getStreamTimeoutMs(), TimeUnit.MILLISECONDS).join();
        } catch (Exception ex) {
            log.warn("DOTVOICE_STREAM_TIMEOUT callId={} waitedMs={} webSocketInputClosed={} webSocketOutputClosed={} playbackContextPresent={} pendingStartPresent={} framesSent={}",
                    providerCallId, properties.getStreamTimeoutMs(), stream.socket().isInputClosed(),
                    stream.socket().isOutputClosed(), stream.active().get() != null,
                    pendingStarts.containsKey(providerCallId), context.sent.get());
            return new PlaybackResult(false, "STREAM_START_TIMEOUT", "STREAM_START_TIMEOUT", context.sent.get(), null);
        } finally {
            stream.active().compareAndSet(context, null);
            activeStreams.remove(providerCallId, stream);
            if (persistentListenerRunning.get()) prepared.compareAndSet(null, stream);
            else try { stream.socket().sendClose(WebSocket.NORMAL_CLOSURE, "playback-complete"); } catch (RuntimeException ignored) { }
        }
    }

    @Override
    public boolean startPlayback(String providerCallId, List<byte[]> mediaFrames) {
        if (!StringUtils.hasText(providerCallId) || mediaFrames == null || mediaFrames.isEmpty()) return false;
        AtomicBoolean lock = playbackLocks.computeIfAbsent(providerCallId, ignored -> new AtomicBoolean());
        if (!lock.compareAndSet(false, true)) {
            log.warn("DOTVOICE_PLAYBACK_REJECTED providerCallId={} reason=playback_already_active", providerCallId);
            return false;
        }
        List<byte[]> immutableFrames = mediaFrames.stream().map(frame -> frame == null ? null : frame.clone()).toList();
        if (immutableFrames.stream().anyMatch(frame -> frame == null || frame.length != 160)) {
            releasePlayback(providerCallId);
            log.warn("DOTVOICE_PLAYBACK_REJECTED providerCallId={} reason=invalid_frame_size", providerCallId);
            return false;
        }
        PreparedStream stream;
        boolean waitingForStreamStart;
        if (inboundCallIds.containsKey(providerCallId)) {
            stream = activeStreams.get(providerCallId);
            waitingForStreamStart = false;
        } else {
            stream = activeStreams.get(providerCallId);
            waitingForStreamStart = stream == null;
            if (stream == null) stream = prepared.get();
        }
        if (stream == null) stream = activeStreams.get(providerCallId);
        if (stream == null) {
            releasePlayback(providerCallId);
            return false;
        }
        PlaybackContext context = new PlaybackContext(providerCallId, immutableFrames, new CompletableFuture<>());
        stream.active.set(context);
        activeStreams.put(providerCallId, stream);
        PendingStart buffered = pendingStarts.remove(providerCallId);
        if (buffered != null && System.currentTimeMillis() - buffered.createdAt() < properties.getStreamTimeoutMs()) {
            startPlayback(stream, context, buffered.node());
        } else if (!waitingForStreamStart) {
            inboundHandlers.forEach(handler -> handler.onPlaybackStarted(providerCallId));
            sendFrames(stream, context, properties.getMediaFrameDurationMs());
        }
        return true;
    }

    @Override
    public boolean hangup(String providerCallId) {
        PreparedStream stream = activeStreams.get(providerCallId);
        if (stream != null && !stream.socket().isOutputClosed()) {
            try {
                if (!sendTextSerially(stream.socket(), objectMapper.writeValueAsString(Map.of(
                        "event", "control",
                        "call_id", providerCallId,
                        "action", "hangup")))) throw new IllegalStateException("WEBSOCKET_SEND_FAILED");
                log.info("DOTVOICE_HANGUP_SENT providerCallId={} transport=websocket", providerCallId);
                return true;
            } catch (Exception ex) {
                log.warn("DOTVOICE_HANGUP_FALLBACK providerCallId={} reason=websocket_send_failed", providerCallId);
            }
        }
        if (dotVoiceHttpClient != null) {
            DotVoiceHttpResponse response = dotVoiceHttpClient.hangupCall(properties, providerCallId);
            if ((response.statusCode() >= 200 && response.statusCode() < 300) || response.statusCode() == 404) {
                log.info("DOTVOICE_HANGUP_SENT providerCallId={} transport=rest status={}", providerCallId, response.statusCode());
                return true;
            }
            log.warn("DOTVOICE_HANGUP_FALLBACK providerCallId={} reason=rest_hangup_failed status={}",
                    providerCallId, response.statusCode());
        }
        return false;
    }

    @Override
    public boolean rejectCall(String providerCallId) {
        PreparedStream stream = activeStreams.get(providerCallId);
        if (!StringUtils.hasText(providerCallId) || stream == null || !inboundCallIds.containsKey(providerCallId)
                || inboundAnswered.containsKey(providerCallId)
                || stream.socket().isOutputClosed()) return false;
        try {
            if (!sendTextSerially(stream.socket(), objectMapper.writeValueAsString(Map.of(
                    "event", "reject_call", "call_id", providerCallId)))) return false;
            log.info("DOTVOICE_REJECT_CALL_SENT callId={}", providerCallId);
            return true;
        } catch (Exception ex) {
            log.warn("DOTVOICE_REJECT_CALL_FAILED callId={} reason={}", providerCallId, sanitize(ex.getMessage()));
            return false;
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
                long callbackStarted = System.nanoTime();
                String callbackEvent = "fragment";
                String callbackCallId = null;
                try {
                    textBuffer.append(data);
                    if (!last) {
                        logCallback(callbackEvent, callbackCallId, callbackStarted);
                        return WebSocket.Listener.super.onText(socket, data, last);
                    }
                    int payloadLength = textBuffer.length();
                    JsonNode node = objectMapper.readTree(textBuffer.toString());
                    textBuffer.setLength(0);
                    String event = resolveEvent(node);
                    String callId = text(node, "call_id", "callId");
                    callbackEvent = event;
                    callbackCallId = callId;
                    if (!"media".equalsIgnoreCase(event)) {
                        log.info("DOTVOICE_CALLBACK_START eventType={} providerCallId={} threadName={}", event, callId,
                                Thread.currentThread().getName());
                    }
                    if (!"media".equalsIgnoreCase(event)) {
                        log.info("DOTVOICE_STREAM_EVENT event={} callId={} keys={}", event, callId, safeKeys(node));
                    }
                    if ("ready".equalsIgnoreCase(event)) {
                        ready.complete(true);
                    } else if ("incoming_call".equalsIgnoreCase(event)) {
                        if (StringUtils.hasText(callId)) {
                            activeStreams.putIfAbsent(callId, new PreparedStream(socket, new AtomicReference<>()));
                            inboundCallIds.put(callId, true);
                            inboundHandlers.forEach(handler -> handler.onIncomingCall(
                                    callId, rawText(node, "from_number", "from", "caller", "caller_number"),
                                    rawText(node, "to_number", "to", "did", "did_number")));
                        }
                    } else if ("error".equalsIgnoreCase(event)) {
                        logProviderError(node, callId);
                        ready.completeExceptionally(new StreamReadinessException("STREAM_PROVIDER_REJECTED"));
                    } else if ("stream_start".equalsIgnoreCase(event)) {
                        String direction = text(node, "direction");
                        log.info("DOTVOICE_STREAM_START_RECEIVED providerCallId={} direction={}", callId, direction);
                        PreparedStream callStream = StringUtils.hasText(callId) ? activeStreams.get(callId) : null;
                        PlaybackContext context = callStream == null ? null : callStream.active().get();
                        if (context == null) {
                            PlaybackContext listenerContext = active.get();
                            context = listenerContext != null && listenerContext.callId.equals(callId) ? listenerContext : null;
                        }
                        if (context == null) {
                            if (StringUtils.hasText(callId)) {
                                if (inboundCallIds.containsKey(callId) && !"inbound".equalsIgnoreCase(direction)) {
                                    log.warn("DOTVOICE_STREAM_START_IGNORED callId={} reason=inbound_direction_required", callId);
                                } else if (activeStreams.containsKey(callId)) {
                                    if (inboundCallIds.containsKey(callId)) inboundAnswered.put(callId, true);
                                    if (inboundCallIds.containsKey(callId) && "inbound".equalsIgnoreCase(direction)) {
                                        log.info("DOTVOICE_INBOUND_STREAM_STARTED callId={}", callId);
                                    }
                                    log.info("DOTVOICE_STREAM_MATCH callId={} matched=inbound", callId);
                                    inboundHandlers.forEach(handler -> handler.onStreamStartCorrelation(callId, false, false));
                                    inboundHandlers.forEach(handler -> handler.onStreamStart(callId));
                                } else {
                                    pendingStarts.put(callId, new PendingStart(node, System.currentTimeMillis()));
                                    log.info("DOTVOICE_STREAM_START_CORRELATION providerCallId={} activePlaybackMatched={} pendingStartPresent={} responseContextPresent={}",
                                            callId, false, true, false);
                                }
                            }
                            pendingStarts.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().createdAt() > properties.getStreamTimeoutMs());
                        } else if (context.callId.equals(callId)) {
                            log.info("DOTVOICE_STREAM_MATCH callId={} matched=true", callId);
                            inboundHandlers.forEach(handler -> handler.onStreamStartCorrelation(callId, true, false));
                            PreparedStream matchedStream = activeStreams.get(callId);
                            if (matchedStream != null) startPlayback(matchedStream, context, node);
                        } else {
                            log.info("DOTVOICE_STREAM_MATCH callId={} matched=false", callId);
                            log.info("DOTVOICE_STREAM_START_CORRELATION providerCallId={} activePlaybackMatched={} pendingStartPresent={} responseContextPresent={}",
                                    callId, false, false, false);
                        }
                    } else if ("media".equalsIgnoreCase(event)) {
                        if (StringUtils.hasText(callId)) {
                            String audio = text(node, "audio", "payload", "data");
                            if (StringUtils.hasText(audio)) {
                                try {
                                    inboundHandlers.forEach(handler -> handler.onMedia(callId, Base64.getDecoder().decode(audio)));
                                } catch (IllegalArgumentException ex) {
                                    log.warn("DOTVOICE_STREAM_MEDIA_REJECTED callId={} reason=malformed_base64", callId);
                                }
                            }
                        }
                    } else if ("stream_stop".equalsIgnoreCase(event)) {
                        PlaybackContext stoppedPlayback = null;
                        if (StringUtils.hasText(callId)) {
                            log.info("DOTVOICE_STREAM_STOP_RECEIVED callId={}", callId);
                            PreparedStream stoppedStream = activeStreams.get(callId);
                            stoppedPlayback = stoppedStream == null ? null : stoppedStream.active().get();
                            if (stoppedPlayback != null) {
                                finishPlayback(stoppedStream, stoppedPlayback, true, null);
                                stoppedPlayback.completion.complete(new PlaybackResult(true, "PLAYBACK_COMPLETED", null,
                                        stoppedPlayback.sent.get(), text(node, "cause")));
                                stoppedStream.active().compareAndSet(stoppedPlayback, null);
                            }
                            inboundHandlers.forEach(handler -> handler.onStreamStop(callId, text(node, "cause")));
                            activeStreams.remove(callId);
                            inboundCallIds.remove(callId);
                            inboundAnswered.remove(callId);
                            pendingStarts.remove(callId);
                        }
                        if (stoppedPlayback == null && active.get() != null && active.get().callId.equals(callId)) {
                            PlaybackContext listenerContext = active.get();
                            listenerContext.completion.complete(new PlaybackResult(true, "PLAYBACK_COMPLETED", null,
                                    listenerContext.sent.get(), text(node, "cause")));
                    } else if (!isKnownEvent(event)) {
                        logUnknownEvent(node, event, callId, payloadLength);
                        }
                    }
                } catch (Exception ex) {
                    PlaybackContext context = active.get();
                    if (context != null) context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", "STREAM_DISCONNECTED", context.sent.get(), null));
                }
                logCallback(callbackEvent, callbackCallId, callbackStarted);
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
                if (persistentListenerRunning.get()) {
                    if (prepared.get() != null && prepared.get().socket() == socket) prepared.set(null);
                    listenerState = "DISCONNECTED";
                    log.info("DOTVOICE_INBOUND_LISTENER_DISCONNECTED");
                    scheduleReconnect();
                }
                return WebSocket.Listener.super.onClose(socket, statusCode, reason);
            }

            @Override public void onError(WebSocket socket, Throwable error) {
                ready.complete(false);
                if (persistentListenerRunning.get()) {
                    listenerState = "DISCONNECTED";
                    log.info("DOTVOICE_INBOUND_LISTENER_DISCONNECTED");
                    scheduleReconnect();
                }
                PlaybackContext context = active.get();
                if (context != null) context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", "STREAM_DISCONNECTED", context.sent.get(), null));
            }
        };
    }

    private void logCallback(String event, String callId, long startedAt) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        if ("media".equalsIgnoreCase(event) && elapsedMs < 20) return;
        log.info("DOTVOICE_CALLBACK_END eventType={} providerCallId={} threadName={} elapsedMs={}", event, callId,
                Thread.currentThread().getName(), elapsedMs);
    }

    private void startPlayback(PreparedStream stream, PlaybackContext context, JsonNode node) {
        if (!context.started.compareAndSet(false, true)) return;
        inboundHandlers.forEach(handler -> handler.onStreamStart(context.callId));
        WebSocket socket = stream.socket();
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
        sendFrames(stream, context, properties.getMediaFrameDurationMs());
    }

    private void sendFrames(PreparedStream stream, PlaybackContext context, int frameDurationMs) {
        if (!playbackAdmission.tryAcquire()) {
            log.warn("DOTVOICE_PLAYBACK_REJECTED providerCallId={} playbackId={} reason=writer_capacity activePlaybackCount={}",
                    context.callId, context.playbackId, properties.getPlaybackQueueCapacity() - playbackAdmission.availablePermits());
            finishPlayback(stream, context, false, "PLAYBACK_WRITER_REJECTED");
            return;
        }
        context.admitted.set(true);
        context.startedAt = System.currentTimeMillis();
        context.frameDurationMs = Math.max(0, frameDurationMs);
        log.info("DOTVOICE_PLAYBACK_SUBMITTED providerCallId={} playbackId={} frameCount={}",
                context.callId, context.playbackId, context.frames.size());
        schedulePlaybackFrame(stream, context);
    }

    private boolean sendTextSerially(WebSocket socket, String payload) {
        if (!controlWriteAdmission.tryAcquire()) {
            log.warn("DOTVOICE_SOCKET_WRITER_REJECTED reason=control_capacity queueCapacity={}",
                    properties.getPlaybackQueueCapacity());
            return false;
        }
        CompletableFuture<Boolean> sent = new CompletableFuture<>();
        try {
            playbackWriter.execute(() -> {
                try {
                    socket.sendText(payload, true).join();
                    sent.complete(true);
                } catch (RuntimeException ex) {
                    sent.completeExceptionally(ex);
                } finally {
                    controlWriteAdmission.release();
                }
            });
        } catch (RuntimeException ex) {
            controlWriteAdmission.release();
            log.warn("DOTVOICE_SOCKET_WRITER_REJECTED reason=executor_shutdown queueCapacity={}",
                    properties.getPlaybackQueueCapacity());
            return false;
        }
        try { return sent.join(); }
        catch (RuntimeException ex) { return false; }
    }

    private void schedulePlaybackFrame(PreparedStream stream, PlaybackContext context) {
        try {
            context.nextFrame.set(playbackWriter.schedule(() -> writePlaybackFrame(stream, context),
                    context.sent.get() == 0 ? 0 : context.frameDurationMs, TimeUnit.MILLISECONDS));
        } catch (RejectedExecutionException ex) {
            finishPlayback(stream, context, false, "PLAYBACK_WRITER_REJECTED");
        }
    }

    private void writePlaybackFrame(PreparedStream stream, PlaybackContext context) {
        if (context.cancelled.get() || stream.socket().isOutputClosed()) {
            finishPlayback(stream, context, false, "PLAYBACK_CANCELLED");
            return;
        }
        if (context.sent.get() == 0) log.info("DOTVOICE_PLAYBACK_STARTED providerCallId={} playbackId={} frameCount={} writerThread={}",
                context.callId, context.playbackId, context.frames.size(), Thread.currentThread().getName());
        try {
            int index = context.sent.get();
            byte[] frame = context.frames.get(index);
            stream.socket().sendText(objectMapper.writeValueAsString(Map.of("event", "media", "call_id", context.callId,
                    "audio", Base64.getEncoder().encodeToString(frame))), true).join();
            int sent = context.sent.incrementAndGet();
            if (sent == context.frames.size()) {
                finishPlayback(stream, context, true, null);
            } else {
                schedulePlaybackFrame(stream, context);
            }
        } catch (Exception ex) {
            finishPlayback(stream, context, false, "STREAM_DISCONNECTED");
        }
    }

    private void finishPlayback(PreparedStream stream, PlaybackContext context, boolean success, String failure) {
        if (!context.finished.compareAndSet(false, true)) return;
        ScheduledFuture<?> next = context.nextFrame.getAndSet(null);
        if (next != null) next.cancel(false);
        if (!success) stream.active().compareAndSet(context, null);
        if (success) {
            log.info("DOTVOICE_PLAYBACK_FINISHED providerCallId={} playbackId={} frameCount={} elapsedMs={} writerThread={}",
                    context.callId, context.playbackId, context.sent.get(), System.currentTimeMillis() - context.startedAt,
                    Thread.currentThread().getName());
            inboundHandlers.forEach(handler -> handler.onPlaybackComplete(context.callId));
        } else {
            log.warn("DOTVOICE_PLAYBACK_FINISHED providerCallId={} playbackId={} frameCount={} elapsedMs={} status=FAILED failure={} writerThread={}",
                    context.callId, context.playbackId, context.sent.get(), System.currentTimeMillis() - context.startedAt, failure,
                    Thread.currentThread().getName());
            context.completion.complete(new PlaybackResult(false, "PLAYBACK_FAILED", failure, context.sent.get(), null));
            inboundHandlers.forEach(handler -> handler.onPlaybackPreparationFailed(context.callId));
        }
        if (context.admitted.compareAndSet(true, false)) playbackAdmission.release();
        releasePlayback(context.callId);
    }

    private void releasePlayback(String providerCallId) {
        AtomicBoolean lock = playbackLocks.get(providerCallId);
        if (lock != null && lock.compareAndSet(true, false)) {
            playbackLocks.remove(providerCallId, lock);
        }
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

    private void connectManaged(long delaySeconds) {
        if (!persistentListenerRunning.get()) return;
        if (delaySeconds > 0) {
            listenerState = "RECONNECTING";
            log.info("DOTVOICE_INBOUND_LISTENER_RECONNECTING delaySeconds={}", delaySeconds);
            reconnectTask.set(reconnectScheduler.schedule(() -> connectManaged(0), delaySeconds, TimeUnit.SECONDS));
            return;
        }
        reconnectTask.set(null);
        boolean connected = prepare();
        if (connected) {
            reconnectAttempts.set(0);
            log.info("DOTVOICE_INBOUND_LISTENER_CONNECTED");
        }
        else if (persistentListenerRunning.get()) scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!persistentListenerRunning.get() || reconnectTask.get() != null) return;
        int attempt = reconnectAttempts.getAndIncrement();
        long delay = switch (attempt) {
            case 0 -> 1L;
            case 1 -> 2L;
            case 2 -> 5L;
            case 3 -> 10L;
            default -> 30L;
        };
        connectManaged(delay);
    }

    private void closeAllStreams() {
        closePrepared();
        activeStreams.values().forEach(stream -> {
            PlaybackContext context = stream.active().get();
            if (context != null) {
                context.cancelled.set(true);
                finishPlayback(stream, context, false, "LISTENER_SHUTDOWN");
            }
            try { stream.socket().sendClose(WebSocket.NORMAL_CLOSURE, "shutdown"); } catch (RuntimeException ignored) { }
        });
        activeStreams.clear();
        inboundCallIds.clear();
        inboundAnswered.clear();
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

    private String rawText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node == null ? null : node.get(name);
            if (value != null && !value.isNull() && value.isValueNode() && StringUtils.hasText(value.asText())) return value.asText();
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
            case "ready", "incoming_call", "stream_start", "media", "stream_stop", "control" -> true;
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
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean admitted = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> nextFrame = new AtomicReference<>();
        private final String playbackId = java.util.UUID.randomUUID().toString();
        private volatile long startedAt;
        private volatile int frameDurationMs;
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
