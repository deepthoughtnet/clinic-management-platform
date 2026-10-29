package com.deepthoughtnet.clinic.api.platform.communicationtest;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceConversationState;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.DotVoiceInboundMediaHandler;
import com.deepthoughtnet.clinic.carepilot.ai_call.provider.G711MuLawAudioConverter;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "communication-test-voice-turn");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public CommunicationTestDotVoiceInboundHandler(@Lazy CommunicationTestService service, VoiceTestProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    public CommunicationTestDotVoiceInboundHandler(@Lazy CommunicationTestService service) {
        this(service, new VoiceTestProperties());
    }

    @Override
    public void onIncomingCall(String providerCallId, String callerPhoneNumber, String didNumber) {
        service.registerInboundCall(providerCallId, callerPhoneNumber, didNumber);
        inboundConversations.put(providerCallId, new ConversationRuntime(true));
        log.info("DOTVOICE_INBOUND_CONTEXT_REGISTERED callId={}", providerCallId);
    }

    @Override
    public void onStreamStart(String providerCallId) {
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
        ConversationRuntime runtime = conversations.get(providerCallId);
        boolean inbound = false;
        if (runtime == null) {
            runtime = inboundConversations.get(providerCallId);
            inbound = runtime != null;
        }
        if (runtime == null) return;
        byte[] audio;
        synchronized (runtime) {
            if (runtime.inbound && runtime.state == VoiceConversationState.LISTENING
                    && runtime.speechDetected && runtime.speechBytes >= minSpeechBytes()
                    && !runtime.processing.get()) {
                finalizeUtterance(providerCallId, runtime, "stream_stop");
            }
            audio = runtime.buffer.toByteArray();
        }
        log.info("DOTVOICE_STREAM_STOP_RECEIVED providerCallId={} cause={} bufferedBytes={} chunkCount={}",
                providerCallId, cause, audio.length, runtime.chunkCount);
        cancel(runtime);
        if (conversations.remove(providerCallId, runtime)) inbound = false;
        else if (inboundConversations.remove(providerCallId, runtime)) inbound = true;
        log.info("DOTVOICE_INBOUND_CONTEXT_REMOVED callId={}", providerCallId);
        if (inbound) service.onInboundStreamStopped(providerCallId, cause);
        else service.onInteractiveStreamStopped(providerCallId, cause);
    }

    private void scheduleSilenceCheck(String providerCallId, ConversationRuntime runtime) {
        if (runtime.silenceFuture != null && !runtime.silenceFuture.isDone()) return;
        runtime.silenceFuture = scheduler.schedule(() -> checkSilence(providerCallId, runtime),
                Math.max(100, properties.getVad().getSilenceTimeoutMs()), TimeUnit.MILLISECONDS);
    }

    private void checkSilence(String providerCallId, ConversationRuntime runtime) {
        synchronized (runtime) {
            runtime.silenceFuture = null;
            if (!(runtime.inbound ? inboundConversations.containsKey(providerCallId) : conversations.containsKey(providerCallId))
                    || runtime.processing.get() || !runtime.speechDetected) return;
            long quietFor = System.currentTimeMillis() - runtime.lastSpeechAt;
            if (quietFor >= properties.getVad().getSilenceTimeoutMs()) {
                finalizeUtterance(providerCallId, runtime, "silence");
            } else {
                scheduleSilenceCheck(providerCallId, runtime);
            }
        }
    }

    private void scheduleNoResponseCheck(String providerCallId, ConversationRuntime runtime) {
        if (runtime.noResponseFuture != null && !runtime.noResponseFuture.isDone()) return;
        runtime.noResponseFuture = scheduler.schedule(() -> {
            synchronized (runtime) {
                if (conversations.containsKey(providerCallId) && !runtime.speechDetected && runtime.processing.compareAndSet(false, true)) {
                    service.processNoResponse(providerCallId);
                }
            }
        }, Math.max(100, properties.getVad().getNoResponseTimeoutMs()), TimeUnit.MILLISECONDS);
    }

    private void finalizeUtterance(String providerCallId, ConversationRuntime runtime, String reason) {
        if (!runtime.processing.compareAndSet(false, true)) return;
        byte[] audio = runtime.buffer.toByteArray();
        if (runtime.inbound) {
            log.info("DOTVOICE_INBOUND_SPEECH_ENDED callId={} reason={} utteranceBytes={} speechMs={}",
                    providerCallId, reason, audio.length, runtime.speechBytes / (BYTES_PER_MULAW_SECOND / 1_000L));
        } else {
            log.info("COMM_TEST_SPEECH_ENDED providerCallId={} reason={} bufferedBytes={} chunkCount={}",
                    providerCallId, reason, audio.length, runtime.chunkCount);
        }
        if (runtime.inbound) service.processInboundFirstUtterance(providerCallId, G711MuLawAudioConverter.muLawToPcmWav(audio));
        else service.processLiveUtterance(providerCallId, G711MuLawAudioConverter.muLawToPcmWav(audio));
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
        private volatile boolean speechDetected;
        private ConversationRuntime() { this(false); }
        private ConversationRuntime(boolean inbound) { this.inbound = inbound; }
    }

    private record AudioLevel(int peak, double rms) {}
}
