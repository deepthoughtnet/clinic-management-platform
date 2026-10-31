package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceConversationState;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceInboundMediaHandler;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.G711MuLawAudioConverter;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Routes matched DotVoice audio into bounded utterances while the call remains live. */
@Component
public class CommunicationTestDotVoiceInboundHandler implements DotVoiceInboundMediaHandler {
    private static final Logger log = LoggerFactory.getLogger(CommunicationTestDotVoiceInboundHandler.class);
    private static final int BYTES_PER_MULAW_SECOND = 8_000;
    private final CommunicationTestService service;
    private final VoiceTestProperties properties;
    private final ConcurrentHashMap<String, ConversationRuntime> conversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConversationRuntime> inboundConversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CallLane> callLanes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> terminatedCalls = new ConcurrentHashMap<>();
    private static final long TERMINATED_CALL_TTL_MS = TimeUnit.MINUTES.toMillis(10);
    private final ScheduledExecutorService scheduler;
    private final ThreadPoolExecutor workers;

    @Autowired
    public CommunicationTestDotVoiceInboundHandler(@Lazy CommunicationTestService service, VoiceTestProperties properties,
            @org.springframework.beans.factory.annotation.Qualifier("dotVoiceInboundWorker") ThreadPoolExecutor workers,
            @org.springframework.beans.factory.annotation.Qualifier("dotVoiceInboundTimer") ScheduledExecutorService scheduler) {
        this.service = service;
        this.properties = properties;
        this.workers = workers;
        this.scheduler = scheduler;
    }

    public CommunicationTestDotVoiceInboundHandler(@Lazy CommunicationTestService service, VoiceTestProperties properties) {
        this(service, properties, standaloneWorkers(), standaloneTimer());
    }

    public CommunicationTestDotVoiceInboundHandler(@Lazy CommunicationTestService service) {
        this(service, new VoiceTestProperties());
    }

    private static ThreadPoolExecutor standaloneWorkers() {
        return new ThreadPoolExecutor(4, 8, 60, TimeUnit.SECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(16), runnable -> {
                    Thread thread = new Thread(runnable, "dotvoice-inbound-test-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    private static ScheduledExecutorService standaloneTimer() {
        return java.util.concurrent.Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "dotvoice-inbound-test-timer");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void onIncomingCall(String providerCallId, String callerPhoneNumber, String didNumber) {
        if (isTerminated(providerCallId)) return;
        ConversationRuntime runtime = new ConversationRuntime(true);
        if (inboundConversations.putIfAbsent(providerCallId, runtime) != null) return;
        enqueue(providerCallId, "incoming_call", () -> {
            service.registerInboundCall(providerCallId, callerPhoneNumber, didNumber);
            log.info("DOTVOICE_INBOUND_CONTEXT_REGISTERED callId={}", providerCallId);
        });
    }

    @Override
    public void onStreamStart(String providerCallId) {
        enqueue(providerCallId, "stream_start", () -> handleStreamStart(providerCallId));
    }

    private void handleStreamStart(String providerCallId) {
        var inboundResult = service.inboundVoiceResult(providerCallId);
        ConversationRuntime inboundRuntime = inboundConversations.get(providerCallId);
        log.info("DOTVOICE_INBOUND_CONTEXT_LOOKUP callId={} found={}", providerCallId,
                inboundResult != null && inboundRuntime != null);
        if (inboundResult != null && !"RESOLVED".equals(inboundResult.routingStatus())) {
            log.info("DOTVOICE_INBOUND_STREAM_IGNORED callId={} routingStatus={} reason={}", providerCallId,
                    inboundResult.routingStatus(), inboundResult.routingFailureReason());
            return;
        }
        if (inboundResult != null && inboundRuntime != null) {
            service.markInboundAnswered(providerCallId);
            inboundRuntime.state = VoiceConversationState.PROMPTING;
            inboundRuntime.botSpeaking.set(true);
            service.startInboundGreeting(providerCallId);
            return;
        }
        var result = service.voiceResultByProviderCallId(providerCallId);
        log.info("DOTVOICE_STREAM_START_RECEIVED providerCallId={}", providerCallId);
        if (result != null) {
            service.markVoiceAnswered(providerCallId);
            ConversationRuntime runtime = new ConversationRuntime();
            runtime.state = VoiceConversationState.PROMPTING;
            runtime.botSpeaking.set(true);
            conversations.putIfAbsent(providerCallId, runtime);
        }
    }

    @Override
    public void onStreamStartCorrelation(String providerCallId, boolean activePlaybackMatched, boolean pendingStartPresent) {
        var result = service.voiceResultByProviderCallId(providerCallId);
        log.info("DOTVOICE_STREAM_START_CORRELATION providerCallId={} activePlaybackMatched={} pendingStartPresent={} responseContextPresent={} testExecutionId={}",
                providerCallId, activePlaybackMatched, pendingStartPresent,
                result != null || service.inboundVoiceResult(providerCallId) != null,
                result == null ? null : result.testExecutionId());
    }

    @Override
    public void onMedia(String providerCallId, byte[] muLawAudio) {
        ConversationRuntime runtime = conversations.get(providerCallId);
        boolean inbound = false;
        if (runtime == null) {
            runtime = inboundConversations.get(providerCallId);
            inbound = runtime != null;
        }
        if (inbound) {
            var inboundResult = service.inboundVoiceResult(providerCallId);
            if (inboundResult == null || !"RESOLVED".equals(inboundResult.routingStatus())) return;
        }
        if (runtime == null || muLawAudio == null || muLawAudio.length == 0) return;
        if (inbound) runtime.receivedChunkCount++;
        if (inbound && (runtime.state != VoiceConversationState.LISTENING || runtime.botSpeaking.get())) {
            if (runtime.receivedChunkCount == 1 || runtime.receivedChunkCount % 50 == 0) {
                log.info("DOTVOICE_INBOUND_MEDIA_SKIPPED callId={} state={} botSpeaking={} reason=not_listening",
                        providerCallId, runtime.state, runtime.botSpeaking.get());
            }
            return;
        }
        if (runtime.botSpeaking.get()) return;
        synchronized (runtime) {
            if (runtime.processing.get() || runtime.buffer.size() >= maxUtteranceBytes()) return;
            runtime.buffer.writeBytes(muLawAudio);
            runtime.lastMediaAt = System.currentTimeMillis();
            runtime.chunkCount++;
            runtime.cumulativeBytes += muLawAudio.length;
            AudioLevel level = audioLevel(muLawAudio);
            if (runtime.inbound && (runtime.chunkCount == 1 || runtime.chunkCount % 50 == 0)) {
                log.info("DOTVOICE_INBOUND_AUDIO_LEVEL callId={} frames={} pcmSamples={} peak={} rms={}",
                        providerCallId, runtime.chunkCount, runtime.cumulativeBytes, level.peak(), level.rms());
            }
            boolean speech = level.rms() >= properties.getVad().getSpeechStartThreshold();
            boolean speechStarted = false;
            if (speech && !runtime.speechDetected) {
                runtime.speechDetected = true;
                speechStarted = true;
                runtime.speechBytes = muLawAudio.length;
                runtime.lastSpeechAt = runtime.lastMediaAt;
                if (runtime.inbound) {
                    log.info("DOTVOICE_INBOUND_SPEECH_STARTED callId={} bufferedBytes={}", providerCallId, runtime.buffer.size());
                } else {
                    log.info("COMM_TEST_SPEECH_STARTED providerCallId={}", providerCallId);
                }
            }
            if (runtime.speechDetected) {
                if (level.rms() >= properties.getVad().getSpeechEndThreshold()) {
                    runtime.lastSpeechAt = runtime.lastMediaAt;
                    if (speech && !speechStarted) runtime.speechBytes += muLawAudio.length;
                }
                scheduleSilenceCheck(providerCallId, runtime);
            }
            if (runtime.chunkCount == 1 || runtime.chunkCount % 50 == 0) {
                log.info("DOTVOICE_MEDIA_RECEIVED providerCallId={} chunkCount={} cumulativeBytes={}",
                        providerCallId, runtime.chunkCount, runtime.cumulativeBytes);
            }
            if (!inbound) service.markVoiceStatus(providerCallId, "LISTENING");
        }
    }

    @Override
    public void onPlaybackStarted(String providerCallId) {
        ConversationRuntime runtime = conversations.get(providerCallId);
        if (runtime == null) runtime = inboundConversations.get(providerCallId);
        if (runtime == null) return;
        runtime.botSpeaking.set(true);
        var result = service.voiceResultByProviderCallId(providerCallId);
        var inboundResult = service.inboundVoiceResult(providerCallId);
        if (runtime.inbound && inboundResult != null && !"CLOSING".equals(inboundResult.status())) {
            synchronized (runtime) {
                runtime.buffer.reset();
                runtime.speechDetected = false;
                runtime.speechBytes = 0;
                runtime.lastSpeechAt = 0;
                runtime.chunkCount = 0;
                runtime.cumulativeBytes = 0;
            }
        } else if (!runtime.inbound && result != null && "REPROMPTING".equals(result.status())) {
            synchronized (runtime) {
                runtime.buffer.reset();
                runtime.speechDetected = false;
                runtime.lastSpeechAt = 0;
                runtime.chunkCount = 0;
                runtime.cumulativeBytes = 0;
            }
        }
        runtime.state = (runtime.inbound && inboundResult != null && "CLOSING".equals(inboundResult.status()))
                || (!runtime.inbound && result != null && "CLOSING".equals(result.status()))
                ? VoiceConversationState.CLOSING : VoiceConversationState.REPROMPTING;
    }

    @Override
    public void onPlaybackComplete(String providerCallId) {
        enqueue(providerCallId, "playback_complete", () -> handlePlaybackComplete(providerCallId));
    }

    private void handlePlaybackComplete(String providerCallId) {
        ConversationRuntime runtime = conversations.get(providerCallId);
        if (runtime == null) runtime = inboundConversations.get(providerCallId);
        if (runtime == null) return;
        runtime.botSpeaking.set(false);
        if (runtime.inbound) {
            var inboundResult = service.inboundVoiceResult(providerCallId);
            if (inboundResult == null || !"RESOLVED".equals(inboundResult.routingStatus())) return;
            if (runtime.state == VoiceConversationState.CLOSING) {
                service.onInboundClosingPlaybackComplete(providerCallId);
            } else {
                runtime.state = VoiceConversationState.LISTENING;
                log.info("DOTVOICE_INBOUND_LISTENING_STARTED callId={}", providerCallId);
                service.markInboundListening(providerCallId);
            }
            return;
        }
        if (runtime.state == VoiceConversationState.CLOSING) {
            service.onClosingPlaybackComplete(providerCallId);
        } else {
            runtime.state = VoiceConversationState.LISTENING;
            service.onInteractivePlaybackComplete(providerCallId);
            scheduleNoResponseCheck(providerCallId, runtime);
        }
    }

    @Override
    public void onPlaybackPreparationFailed(String providerCallId) {
        ConversationRuntime runtime = inboundConversations.get(providerCallId);
        if (runtime == null) return;
        runtime.botSpeaking.set(false);
        if (runtime.state == VoiceConversationState.CLOSING) return;
        synchronized (runtime) {
            runtime.buffer.reset();
            runtime.speechDetected = false;
            runtime.speechBytes = 0;
            runtime.lastSpeechAt = 0;
            runtime.chunkCount = 0;
            runtime.cumulativeBytes = 0;
            runtime.state = VoiceConversationState.LISTENING;
        }
        service.markInboundListening(providerCallId);
        log.info("DOTVOICE_INBOUND_LISTENING_STARTED callId={} reason=PLAYBACK_PREPARATION_FAILED", providerCallId);
    }

    @Override
    public void onStreamStop(String providerCallId, String cause) {
        enqueue(providerCallId, "stream_stop", () -> handleStreamStop(providerCallId, cause));
    }

    private void handleStreamStop(String providerCallId, String cause) {
        ConversationRuntime runtime = conversations.get(providerCallId);
        boolean inbound = false;
        if (runtime == null) {
            runtime = inboundConversations.get(providerCallId);
            inbound = runtime != null;
        }
        if (runtime == null) return;
        byte[] audio;
        byte[] finalAudio = null;
        synchronized (runtime) {
            if (runtime.inbound && runtime.state == VoiceConversationState.LISTENING
                    && runtime.speechDetected && runtime.speechBytes >= minSpeechBytes()
                    && runtime.processing.compareAndSet(false, true)) {
                finalAudio = runtime.buffer.toByteArray();
            }
            audio = runtime.buffer.toByteArray();
        }
        log.info("DOTVOICE_STREAM_STOP_RECEIVED providerCallId={} cause={} bufferedBytes={} chunkCount={}",
                providerCallId, cause, audio.length, runtime.chunkCount);
        if (finalAudio != null) processCapturedUtterance(providerCallId, runtime, finalAudio, "stream_stop");
        cancel(runtime);
        if (conversations.remove(providerCallId, runtime)) inbound = false;
        else if (inboundConversations.remove(providerCallId, runtime)) inbound = true;
        log.info("DOTVOICE_INBOUND_CONTEXT_REMOVED callId={}", providerCallId);
        markTerminated(providerCallId);
        CallLane lane = callLanes.get(providerCallId);
        if (lane != null) {
            synchronized (lane) {
                lane.terminated = true;
                lane.tasks.clear();
            }
        }
        callLanes.remove(providerCallId);
        if (inbound) service.onInboundStreamStopped(providerCallId, cause);
        else service.onInteractiveStreamStopped(providerCallId, cause);
    }

    private void scheduleSilenceCheck(String providerCallId, ConversationRuntime runtime) {
        if (runtime.silenceFuture != null && !runtime.silenceFuture.isDone()) return;
        runtime.silenceFuture = scheduler.schedule(() -> checkSilence(providerCallId, runtime),
                Math.max(100, properties.getVad().getSilenceTimeoutMs()), TimeUnit.MILLISECONDS);
    }

    private void checkSilence(String providerCallId, ConversationRuntime runtime) {
        byte[] audio = null;
        String reason = "silence";
        synchronized (runtime) {
            runtime.silenceFuture = null;
            if (!(runtime.inbound ? inboundConversations.containsKey(providerCallId) : conversations.containsKey(providerCallId))
                    || runtime.processing.get() || !runtime.speechDetected) return;
            long quietFor = System.currentTimeMillis() - runtime.lastSpeechAt;
            if (quietFor >= properties.getVad().getSilenceTimeoutMs()) {
                if (runtime.processing.compareAndSet(false, true)) audio = runtime.buffer.toByteArray();
            } else {
                scheduleSilenceCheck(providerCallId, runtime);
            }
        }
        if (audio != null) {
            byte[] captured = audio;
            enqueue(providerCallId, "utterance", () -> processCapturedUtterance(providerCallId, runtime, captured, reason));
        }
    }

    private void scheduleNoResponseCheck(String providerCallId, ConversationRuntime runtime) {
        if (runtime.noResponseFuture != null && !runtime.noResponseFuture.isDone()) return;
        runtime.noResponseFuture = scheduler.schedule(() -> {
            enqueue(providerCallId, "no_response", () -> {
                synchronized (runtime) {
                    if (!conversations.containsKey(providerCallId) || runtime.speechDetected
                            || !runtime.processing.compareAndSet(false, true)) return;
                }
                try { service.processNoResponse(providerCallId); }
                finally { runtime.processing.set(false); }
            });
        }, Math.max(100, properties.getVad().getNoResponseTimeoutMs()), TimeUnit.MILLISECONDS);
    }

    private void processCapturedUtterance(String providerCallId, ConversationRuntime runtime, byte[] audio, String reason) {
        if (runtime.inbound) {
            log.info("DOTVOICE_INBOUND_SPEECH_ENDED callId={} reason={} utteranceBytes={} speechMs={}",
                    providerCallId, reason, audio.length, runtime.speechBytes / (BYTES_PER_MULAW_SECOND / 1_000L));
        } else {
            log.info("COMM_TEST_SPEECH_ENDED providerCallId={} reason={} bufferedBytes={} chunkCount={}",
                    providerCallId, reason, audio.length, runtime.chunkCount);
        }
        long conversionStarted = System.nanoTime();
        log.info("VOICE_STAGE_START stage=AUDIO_CONVERSION providerCallId={} threadName={}", providerCallId,
                Thread.currentThread().getName());
        byte[] wav = G711MuLawAudioConverter.muLawToPcmWav(audio);
        log.info("VOICE_STAGE_END stage=AUDIO_CONVERSION providerCallId={} elapsedMs={} threadName={}", providerCallId,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - conversionStarted), Thread.currentThread().getName());
        long stageStarted = System.nanoTime();
        log.info("VOICE_STAGE_START stage=STT_AIVA_TTS providerCallId={} threadName={}", providerCallId, Thread.currentThread().getName());
        if (runtime.inbound) service.processInboundFirstUtterance(providerCallId, wav);
        else service.processLiveUtterance(providerCallId, wav);
        log.info("VOICE_STAGE_END stage=STT_AIVA_TTS providerCallId={} elapsedMs={} threadName={}", providerCallId,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stageStarted), Thread.currentThread().getName());
        var result = service.voiceResultByProviderCallId(providerCallId);
        if (runtime.inbound) {
            var inboundResult = service.inboundVoiceResult(providerCallId);
            if (inboundResult != null
                    && !"CLOSING".equals(inboundResult.status())
                    && !"HANGUP_REQUESTED".equals(inboundResult.status())) {
                runtime.processing.set(false);
            }
        } else if (result != null && "REPROMPTING".equals(result.status())) {
            runtime.processing.set(false);
        }
    }

    private void enqueue(String providerCallId, String kind, Runnable task) {
        if (providerCallId == null || task == null) return;
        if (isTerminated(providerCallId)) return;
        if (!inboundConversations.containsKey(providerCallId) && !conversations.containsKey(providerCallId)) {
            if (!"incoming_call".equals(kind)
                    && !("stream_start".equals(kind) && service.voiceResultByProviderCallId(providerCallId) != null)) return;
        }
        CallLane lane = callLanes.computeIfAbsent(providerCallId, ignored -> new CallLane());
        ConversationRuntime runtime = inboundConversations.get(providerCallId);
        if (runtime == null) runtime = conversations.get(providerCallId);
        int turnNumber = runtime == null || !"utterance".equals(kind) ? 0 : runtime.turnCount.incrementAndGet();
        String turnId = java.util.UUID.randomUUID().toString();
        long submittedAt = System.currentTimeMillis();
        int queued;
        synchronized (lane) {
            if (lane.terminated) return;
            boolean activeRuntime = inboundConversations.containsKey(providerCallId) || conversations.containsKey(providerCallId);
            boolean activeOutbound = "stream_start".equals(kind)
                    && service.voiceResultByProviderCallId(providerCallId) != null;
            if (!activeRuntime && !"incoming_call".equals(kind) && !activeOutbound) return;
            if (lane.tasks.size() >= 32) {
                log.warn("DOTVOICE_VOICE_EXECUTOR_REJECTED providerCallId={} task={} reason=call_queue_full activeWorkers={} poolSize={} maxPoolSize={} queueDepth={} queueCapacity={}",
                        providerCallId, kind, workers.getActiveCount(), workers.getPoolSize(), workers.getMaximumPoolSize(),
                        workers.getQueue().size(), workers.getQueue().size() + workers.getQueue().remainingCapacity());
                return;
            }
            AtomicReference<String> taskStatus = new AtomicReference<>("COMPLETED");
            lane.tasks.add(() -> {
                long started = System.nanoTime();
                log.info("VOICE_TURN_STARTED providerCallId={} turnNumber={} turnId={} task={} threadName={} queueWaitMs={} activeWorkers={} queueDepth={}",
                        providerCallId, turnNumber, turnId, kind, Thread.currentThread().getName(), System.currentTimeMillis() - submittedAt,
                        workers.getActiveCount(), workers.getQueue().size());
                try { task.run(); }
                catch (RuntimeException ex) {
                    taskStatus.set("FAILED");
                    log.warn("DOTVOICE_CALL_TASK_FAILED providerCallId={} task={} exception={}",
                            providerCallId, kind, ex.getClass().getSimpleName());
                }
                finally {
                    log.info("VOICE_TURN_FINISHED providerCallId={} turnNumber={} turnId={} task={} threadName={} elapsedMs={} status={} activeWorkers={} queueDepth={}",
                            providerCallId, turnNumber, turnId, kind, Thread.currentThread().getName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                            taskStatus.get(), workers.getActiveCount(), workers.getQueue().size());
                    runNext(providerCallId, lane);
                }
            });
            queued = lane.tasks.size();
            if (lane.running) {
                log.info("VOICE_TURN_SUBMITTED providerCallId={} turnNumber={} turnId={} task={} submittedAt={} queueDepth={}",
                        providerCallId, turnNumber, turnId, kind, submittedAt, queued);
                return;
            }
            lane.running = true;
        }
        log.info("VOICE_TURN_SUBMITTED providerCallId={} turnNumber={} turnId={} task={} submittedAt={} activeWorkers={} queueDepth={}",
                providerCallId, turnNumber, turnId, kind, submittedAt, workers.getActiveCount(), workers.getQueue().size());
        submitNext(providerCallId, lane);
    }

    private void runNext(String callId, CallLane lane) {
        synchronized (lane) {
            if (lane.terminated || lane.tasks.isEmpty()) {
                lane.running = false;
                return;
            }
        }
        submitNext(callId, lane);
    }

    private void submitNext(String callId, CallLane lane) {
        Runnable next;
        synchronized (lane) { next = lane.tasks.peek(); }
        if (next == null) return;
        try {
            workers.execute(() -> {
                synchronized (lane) {
                    if (lane.terminated || lane.tasks.peek() != next) return;
                    lane.tasks.poll();
                }
                next.run();
            });
        } catch (RejectedExecutionException ex) {
            synchronized (lane) {
                lane.running = false;
                if (lane.retryScheduled || lane.terminated) return;
                lane.retryScheduled = true;
            }
            log.warn("DOTVOICE_VOICE_EXECUTOR_REJECTED providerCallId={} reason=worker_capacity activeWorkers={} poolSize={} maxPoolSize={} queueDepth={} queueCapacity={}",
                    callId, workers.getActiveCount(), workers.getPoolSize(), workers.getMaximumPoolSize(), workers.getQueue().size(),
                    workers.getQueue().size() + workers.getQueue().remainingCapacity());
            try {
                scheduler.schedule(() -> {
                    synchronized (lane) {
                        lane.retryScheduled = false;
                        if (lane.terminated || lane.running || lane.tasks.isEmpty()) return;
                        lane.running = true;
                    }
                    submitNext(callId, lane);
                }, 250, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException schedulerFailure) {
                synchronized (lane) { lane.retryScheduled = false; }
                log.warn("DOTVOICE_VOICE_RETRY_REJECTED providerCallId={} taskRetained=true", callId);
            }
        }
    }

    private static final class CallLane {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private boolean running;
        private boolean terminated;
        private boolean retryScheduled;
    }

    private boolean isTerminated(String providerCallId) {
        if (providerCallId == null) return false;
        Long endedAt = terminatedCalls.get(providerCallId);
        if (endedAt == null) return false;
        if (System.currentTimeMillis() - endedAt <= TERMINATED_CALL_TTL_MS) return true;
        terminatedCalls.remove(providerCallId, endedAt);
        return false;
    }

    private void markTerminated(String providerCallId) {
        long now = System.currentTimeMillis();
        terminatedCalls.put(providerCallId, now);
        terminatedCalls.entrySet().removeIf(entry -> now - entry.getValue() > TERMINATED_CALL_TTL_MS);
    }

    private int maxUtteranceBytes() {
        return Math.max(BYTES_PER_MULAW_SECOND, properties.getVad().getMaxUtteranceMs() * BYTES_PER_MULAW_SECOND / 1_000);
    }

    private int minSpeechBytes() {
        return Math.max(160, properties.getVad().getMinSpeechMs() * BYTES_PER_MULAW_SECOND / 1_000);
    }

    private AudioLevel audioLevel(byte[] muLaw) {
        long sum = 0;
        int peak = 0;
        for (byte value : muLaw) {
            int sample = decodeMuLaw(value);
            peak = Math.max(peak, Math.abs(sample));
            sum += (long) sample * sample;
        }
        double rms = Math.sqrt(sum / (double) muLaw.length) / Short.MAX_VALUE;
        return new AudioLevel(peak, rms);
    }

    private short decodeMuLaw(byte value) {
        int encoded = (~value) & 0xff;
        int sample = ((encoded & 0x0f) << 3) + 132;
        sample <<= (encoded & 0x70) >> 4;
        return (short) ((encoded & 0x80) != 0 ? 132 - sample : sample - 132);
    }

    private void cancel(ConversationRuntime runtime) {
        if (runtime.silenceFuture != null) runtime.silenceFuture.cancel(false);
        if (runtime.noResponseFuture != null) runtime.noResponseFuture.cancel(false);
    }

    private static final class ConversationRuntime {
        private final boolean inbound;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final AtomicBoolean botSpeaking = new AtomicBoolean();
        private final AtomicBoolean processing = new AtomicBoolean();
        private volatile VoiceConversationState state = VoiceConversationState.PROMPTING;
        private volatile ScheduledFuture<?> silenceFuture;
        private volatile ScheduledFuture<?> noResponseFuture;
        private volatile long lastMediaAt;
        private volatile long lastSpeechAt;
        private volatile int chunkCount;
        private volatile int cumulativeBytes;
        private volatile int receivedChunkCount;
        private volatile int speechBytes;
        private final AtomicInteger turnCount = new AtomicInteger();
        private volatile boolean speechDetected;
        private ConversationRuntime() { this(false); }
        private ConversationRuntime(boolean inbound) { this.inbound = inbound; }
    }

    private record AudioLevel(int peak, double rms) {}
}
