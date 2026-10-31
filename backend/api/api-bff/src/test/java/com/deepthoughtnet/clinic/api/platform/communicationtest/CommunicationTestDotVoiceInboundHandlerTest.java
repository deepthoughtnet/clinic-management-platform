package com.deepthoughtnet.clinic.api.platform.communicationtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.carepilot.ai_call.model.VoiceConversationState;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import org.springframework.test.util.ReflectionTestUtils;

class CommunicationTestDotVoiceInboundHandlerTest {
    @Test
    void differentCallsRunSetupConcurrentlyAndEachGreetingWaitsForItsOwnSetup() throws Exception {
        CommunicationTestService service = mock(CommunicationTestService.class);
        CountDownLatch allEnteredSetup = new CountDownLatch(3);
        CountDownLatch releaseSetup = new CountDownLatch(1);
        CountDownLatch slowGreetingEntered = new CountDownLatch(1);
        CountDownLatch otherGreetingEntered = new CountDownLatch(2);
        CountDownLatch releaseSlowGreeting = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger activeSetups = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger maxConcurrentSetups = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            int active = activeSetups.incrementAndGet();
            maxConcurrentSetups.accumulateAndGet(active, Math::max);
            allEnteredSetup.countDown();
            assertThat(releaseSetup.await(2, TimeUnit.SECONDS)).isTrue();
            activeSetups.decrementAndGet();
            return null;
        }).when(service).registerInboundCall(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        doAnswer(invocation -> {
            String callId = invocation.getArgument(0);
            if ("call-a".equals(callId)) {
                slowGreetingEntered.countDown();
                assertThat(releaseSlowGreeting.await(2, TimeUnit.SECONDS)).isTrue();
            } else {
                otherGreetingEntered.countDown();
            }
            return null;
        }).when(service).startInboundGreeting(org.mockito.ArgumentMatchers.anyString());
        var inbound = resolvedInboundResult();
        when(service.inboundVoiceResult(org.mockito.ArgumentMatchers.anyString())).thenReturn(inbound);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(service,
                new VoiceTestProperties());

        handler.onIncomingCall("call-a", "caller-a", "did");
        handler.onIncomingCall("call-b", "caller-b", "did");
        handler.onIncomingCall("call-c", "caller-c", "did");
        assertThat(allEnteredSetup.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(maxConcurrentSetups.get()).isEqualTo(3);
        handler.onStreamStart("call-a");
        handler.onStreamStart("call-b");
        handler.onStreamStart("call-c");
        releaseSetup.countDown();

        assertThat(slowGreetingEntered.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(otherGreetingEntered.await(2, TimeUnit.SECONDS)).isTrue();
        releaseSlowGreeting.countDown();
        verify(service, timeout(2_000)).startInboundGreeting("call-a");
        verify(service, timeout(2_000)).startInboundGreeting("call-b");
        verify(service, timeout(2_000)).startInboundGreeting("call-c");
        InOrder callAOrder = inOrder(service);
        callAOrder.verify(service).registerInboundCall("call-a", "caller-a", "did");
        callAOrder.verify(service).markInboundAnswered("call-a");
        callAOrder.verify(service).startInboundGreeting("call-a");
        shutdown(handler);
    }

    @Test
    void sameCallTasksRemainSequentialWhileFirstTaskIsBlocked() throws Exception {
        CommunicationTestService service = mock(CommunicationTestService.class);
        CountDownLatch setupComplete = new CountDownLatch(1);
        doAnswer(invocation -> { setupComplete.countDown(); return null; })
                .when(service).registerInboundCall("serial-call", "caller", "did");
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(service,
                new VoiceTestProperties());
        handler.onIncomingCall("serial-call", "caller", "did");
        assertThat(setupComplete.await(2, TimeUnit.SECONDS)).isTrue();

        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(handler, "enqueue", "serial-call", "test-turn-1", (Runnable) () -> {
            firstEntered.countDown();
            try { assertThat(releaseFirst.await(2, TimeUnit.SECONDS)).isTrue(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
        });
        assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(handler, "enqueue", "serial-call", "test-turn-2",
                (Runnable) secondEntered::countDown);
        assertThat(secondEntered.getCount()).isEqualTo(1);
        releaseFirst.countDown();
        assertThat(secondEntered.await(2, TimeUnit.SECONDS)).isTrue();
        shutdown(handler);
    }

    @Test
    void slowRecognitionForOneCallDoesNotBlockAnotherCall() throws Exception {
        CommunicationTestService service = mock(CommunicationTestService.class);
        CountDownLatch setupComplete = new CountDownLatch(2);
        CountDownLatch bothReachedRecognition = new CountDownLatch(2);
        CountDownLatch releaseRecognition = new CountDownLatch(1);
        CountDownLatch bothRecognitionFinished = new CountDownLatch(2);
        java.util.concurrent.atomic.AtomicInteger active = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger maxActive = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> { setupComplete.countDown(); return null; })
                .when(service).registerInboundCall(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        when(service.inboundVoiceResult(org.mockito.ArgumentMatchers.anyString())).thenReturn(resolvedInboundResult());
        doAnswer(invocation -> {
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            bothReachedRecognition.countDown();
            try { assertThat(releaseRecognition.await(2, TimeUnit.SECONDS)).isTrue(); }
            finally { active.decrementAndGet(); bothRecognitionFinished.countDown(); }
            return null;
        }).when(service).processInboundFirstUtterance(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(byte[].class));
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(service,
                new VoiceTestProperties());
        handler.onIncomingCall("stt-a", "caller-a", "did");
        handler.onIncomingCall("stt-b", "caller-b", "did");
        assertThat(setupComplete.await(2, TimeUnit.SECONDS)).isTrue();
        prepareSpeechRuntime(handler, "stt-a");
        prepareSpeechRuntime(handler, "stt-b");

        Object runtimeA = inboundRuntime(handler, "stt-a");
        Object runtimeB = inboundRuntime(handler, "stt-b");
        ReflectionTestUtils.invokeMethod(handler, "checkSilence", "stt-a", runtimeA);
        ReflectionTestUtils.invokeMethod(handler, "checkSilence", "stt-b", runtimeB);

        assertThat(bothReachedRecognition.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(maxActive.get()).isEqualTo(2);
        releaseRecognition.countDown();
        assertThat(bothRecognitionFinished.await(2, TimeUnit.SECONDS)).isTrue();
        shutdown(handler);
    }

    @Test
    void workerQueueSaturationRetainsCallWorkAndNeverRunsItOnSubmittingThread() throws Exception {
        CommunicationTestService service = mock(CommunicationTestService.class);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch allRegistered = new CountDownLatch(3);
        doAnswer(invocation -> {
            String callId = invocation.getArgument(0);
            if ("saturated-a".equals(callId)) {
                firstEntered.countDown();
                assertThat(releaseFirst.await(2, TimeUnit.SECONDS)).isTrue();
            }
            allRegistered.countDown();
            return null;
        }).when(service).registerInboundCall(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        ThreadPoolExecutor workers = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(1), runnable -> {
                    Thread thread = new Thread(runnable, "dotvoice-saturation-test-worker");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        var timer = java.util.concurrent.Executors.newScheduledThreadPool(2);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(
                service, new VoiceTestProperties(), workers, timer);

        handler.onIncomingCall("saturated-a", "caller-a", "did");
        assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
        handler.onIncomingCall("saturated-b", "caller-b", "did");
        handler.onIncomingCall("saturated-c", "caller-c", "did");
        verify(service, org.mockito.Mockito.never()).registerInboundCall("saturated-c", "caller-c", "did");
        releaseFirst.countDown();

        assertThat(allRegistered.await(3, TimeUnit.SECONDS)).isTrue();
        verify(service).registerInboundCall("saturated-c", "caller-c", "did");
        workers.shutdownNow();
        timer.shutdownNow();
    }

    @Test
    void streamStopCleansCallLaneAndLateCallbacksCannotResurrectIt() throws Exception {
        CommunicationTestService service = mock(CommunicationTestService.class);
        CountDownLatch setupComplete = new CountDownLatch(1);
        doAnswer(invocation -> { setupComplete.countDown(); return null; })
                .when(service).registerInboundCall("ended-call", "caller", "did");
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(service,
                new VoiceTestProperties());
        handler.onIncomingCall("ended-call", "caller", "did");
        assertThat(setupComplete.await(2, TimeUnit.SECONDS)).isTrue();

        handler.onStreamStop("ended-call", "normal");
        verify(service, timeout(2_000)).onInboundStreamStopped("ended-call", "normal");
        handler.onPlaybackComplete("ended-call");
        handler.onIncomingCall("ended-call", "caller", "did");

        @SuppressWarnings("unchecked")
        Map<String, Object> lanes = (Map<String, Object>) ReflectionTestUtils.getField(handler, "callLanes");
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimes = (Map<String, Object>) ReflectionTestUtils.getField(handler, "inboundConversations");
        assertThat(lanes).doesNotContainKey("ended-call");
        assertThat(runtimes).doesNotContainKey("ended-call");
        verify(service).registerInboundCall("ended-call", "caller", "did");
        shutdown(handler);
    }

    @Test
    void playbackPreparationFailureReturnsInboundCallToListening() {
        CommunicationTestService service = mock(CommunicationTestService.class);
        CommunicationTestDotVoiceInboundHandler handler = new CommunicationTestDotVoiceInboundHandler(
                service, new VoiceTestProperties());
        String callId = "playback-preparation-failed";
        handler.onIncomingCall(callId, "+10000000000", "+10000000001");
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimes = (Map<String, Object>) ReflectionTestUtils.getField(handler, "inboundConversations");
        Object runtime = runtimes.get(callId);
        ReflectionTestUtils.setField(runtime, "state", VoiceConversationState.PROMPTING);
        ((AtomicBoolean) ReflectionTestUtils.getField(runtime, "botSpeaking")).set(true);

        handler.onPlaybackPreparationFailed(callId);

        assertThat(ReflectionTestUtils.getField(runtime, "state")).isEqualTo(VoiceConversationState.LISTENING);
        assertThat(((AtomicBoolean) ReflectionTestUtils.getField(runtime, "botSpeaking")).get()).isFalse();
        verify(service).markInboundListening(callId);
    }

    private void shutdown(CommunicationTestDotVoiceInboundHandler handler) {
        ((java.util.concurrent.ExecutorService) ReflectionTestUtils.getField(handler, "workers")).shutdownNow();
        ((java.util.concurrent.ExecutorService) ReflectionTestUtils.getField(handler, "scheduler")).shutdownNow();
    }

    private CommunicationTestDtos.InboundVoiceResult resolvedInboundResult() {
        return new CommunicationTestDtos.InboundVoiceResult("call", "caller", "did", "INBOUND", "RINGING",
                "hi-IN", "Sarvam", null, java.time.OffsetDateTime.now(), null, null, null, null, "CONNECTED",
                "did", null, null, null, null, "DOTVOICE", "RESOLVED", null, "+10000000000", "NO_MATCH",
                null, null, 0, "CALLER_PHONE", null, "GUEST_CALLER", null, null, 0, null, null, null, java.util.List.of());
    }

    private Object inboundRuntime(CommunicationTestDotVoiceInboundHandler handler, String callId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> runtimes = (Map<String, Object>) ReflectionTestUtils.getField(handler, "inboundConversations");
        return runtimes.get(callId);
    }

    private void prepareSpeechRuntime(CommunicationTestDotVoiceInboundHandler handler, String callId) {
        Object runtime = inboundRuntime(handler, callId);
        ReflectionTestUtils.setField(runtime, "state", VoiceConversationState.LISTENING);
        ReflectionTestUtils.setField(runtime, "speechDetected", true);
        ReflectionTestUtils.setField(runtime, "speechBytes", 320);
        ReflectionTestUtils.setField(runtime, "lastSpeechAt", 1L);
        ((java.io.ByteArrayOutputStream) ReflectionTestUtils.getField(runtime, "buffer")).writeBytes(new byte[320]);
    }
}
