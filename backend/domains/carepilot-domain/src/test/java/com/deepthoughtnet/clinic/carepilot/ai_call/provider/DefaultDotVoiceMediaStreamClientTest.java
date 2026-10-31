package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.timeout;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultDotVoiceMediaStreamClientTest {
    @Test
    void simultaneousCallsKeepDistinctPlaybackContextsAndSerializeFramesPerCall() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        List<String> sent = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch sixFrames = new CountDownLatch(6);
        when(socket.sendText(any(String.class), org.mockito.ArgumentMatchers.eq(true))).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            sixFrames.countDown();
            return CompletableFuture.completedFuture(socket);
        });
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(
                properties(500), null, client, new ObjectMapper(), List.of());

        assertTrue(stream.prepare());
        listener.get().onText(socket, "{\"event\":\"incoming_call\",\"call_id\":\"call-a\"}", true);
        listener.get().onText(socket, "{\"event\":\"incoming_call\",\"call_id\":\"call-b\"}", true);
        @SuppressWarnings("unchecked")
        var streams = (java.util.Map<String, Object>) org.springframework.test.util.ReflectionTestUtils.getField(stream, "activeStreams");
        Object a = streams.get("call-a");
        Object b = streams.get("call-b");
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(a, "active"))
                .isNotSameAs(org.springframework.test.util.ReflectionTestUtils.getField(b, "active"));

        assertTrue(stream.startPlayback("call-a", List.of(frame(0x01), frame(0x02), frame(0x03))));
        assertTrue(stream.startPlayback("call-b", List.of(frame(0x11), frame(0x12), frame(0x13))));
        assertThat(sixFrames.await(2, TimeUnit.SECONDS)).isTrue();

        ObjectMapper mapper = new ObjectMapper();
        var callAValues = new ArrayList<String>();
        var callBValues = new ArrayList<String>();
        var callOrder = new ArrayList<String>();
        for (String payload : sent) {
            var node = mapper.readTree(payload);
            String target = node.get("call_id").asText();
            callOrder.add(target);
            ("call-a".equals(target) ? callAValues : callBValues).add(node.get("audio").asText());
        }
        assertThat(callOrder).containsExactly("call-a", "call-b", "call-a", "call-b", "call-a", "call-b");
        assertThat(callAValues).containsExactly(
                java.util.Base64.getEncoder().encodeToString(frame(0x01)),
                java.util.Base64.getEncoder().encodeToString(frame(0x02)),
                java.util.Base64.getEncoder().encodeToString(frame(0x03)));
        assertThat(callBValues).containsExactly(
                java.util.Base64.getEncoder().encodeToString(frame(0x11)),
                java.util.Base64.getEncoder().encodeToString(frame(0x12)),
                java.util.Base64.getEncoder().encodeToString(frame(0x13)));
        var activeA = (AtomicReference<?>) org.springframework.test.util.ReflectionTestUtils.getField(a, "active");
        var activeB = (AtomicReference<?>) org.springframework.test.util.ReflectionTestUtils.getField(b, "active");
        assertThat(activeA.get()).isNotNull();
        assertThat(activeB.get()).isNotNull();
        listener.get().onText(socket, "{\"event\":\"stream_stop\",\"call_id\":\"call-a\"}", true);
        assertThat(activeA.get()).isNull();
        assertThat(activeB.get()).isNotNull();
        listener.get().onText(socket, "{\"event\":\"stream_stop\",\"call_id\":\"call-b\"}", true);
        stream.shutdownPlaybackWriter();
    }

    @Test
    void playbackContextRemainsCorrelatedUntilProviderStreamStopCompletesPlay() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        CountDownLatch framesSent = new CountDownLatch(2);
        when(socket.sendText(any(String.class), org.mockito.ArgumentMatchers.eq(true))).thenAnswer(invocation -> {
            if (invocation.<String>getArgument(0).contains("\"event\":\"media\"")) framesSent.countDown();
            return CompletableFuture.completedFuture(socket);
        });
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        DotVoiceProperties properties = properties(1_000);
        properties.setMediaFrameDurationMs(0);
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties, client, new ObjectMapper());

        assertTrue(stream.prepare());
        listener.get().onText(socket,
                "{\"event\":\"stream_start\",\"call_id\":\"play-call\",\"encoding\":\"mulaw\",\"sample_rate\":8000}", true);
        CompletableFuture<DotVoiceMediaStreamClient.PlaybackResult> playback = CompletableFuture.supplyAsync(
                () -> stream.play("play-call", List.of(frame(0x01), frame(0x02))));

        assertTrue(framesSent.await(1, TimeUnit.SECONDS));
        listener.get().onText(socket, "{\"event\":\"stream_stop\",\"call_id\":\"play-call\"}", true);
        DotVoiceMediaStreamClient.PlaybackResult result = playback.get(1, TimeUnit.SECONDS);

        assertTrue(result.success());
        assertThat(result.framesSent()).isEqualTo(2);
        stream.shutdownPlaybackWriter();
    }

    @Test
    void providerErrorBeforeReadyFailsImmediately() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s,
                "{\"event\":\"error\",\"code\":\"POLICY\",\"message\":\"rejected\",\"reason\":\"not authorized\"}", true));
        DotVoiceProperties properties = properties(5000);
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties, client, new ObjectMapper());

        long started = System.nanoTime();
        assertFalse(stream.prepare());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertTrue(elapsedMs < 1000, "provider rejection should not wait for readiness timeout");
    }

    @Test
    void policyCloseBeforeReadyFailsImmediately() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onClose(s, 1008, "policy"));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties(5000), client, new ObjectMapper());

        long started = System.nanoTime();
        assertFalse(stream.prepare());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertTrue(elapsedMs < 1000, "pre-ready close should not wait for readiness timeout");
    }

    @Test
    void openSocketWithoutReadyStillUsesTimeout() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> { });
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties(80), client, new ObjectMapper());

        long started = System.nanoTime();
        assertFalse(stream.prepare());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertTrue(elapsedMs >= 50, "silent socket should retain bounded readiness timeout");
    }

    @Test
    void readyEventPreservesSuccessfulPreparation() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties(500), client, new ObjectMapper());

        assertTrue(stream.prepare());
        verify(socket, atLeastOnce()).request(1);
        stream.abort();
    }

    @Test
    void persistentListenerUsesOneSocketAndReconnectsAfterDisconnect() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        WebSocket.Builder builder = client.newWebSocketBuilder();
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties(500), client, new ObjectMapper());

        stream.startPersistentListener();
        stream.startPersistentListener();
        assertTrue(stream.isConnected());
        assertTrue(stream.connectionState().equals("CONNECTED"));
        verify(builder, timeout(1000).times(1)).buildAsync(any(URI.class), any(WebSocket.Listener.class));

        listener.get().onClose(socket, WebSocket.NORMAL_CLOSURE, "test-disconnect");
        verify(builder, timeout(2500).times(2)).buildAsync(any(URI.class), any(WebSocket.Listener.class));
        stream.stopPersistentListener();
    }

    @Test
    void sendsBotHangupControlOnTheActiveProviderStream() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(String.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(CompletableFuture.completedFuture(socket));
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties(500), client, new ObjectMapper());

        assertTrue(stream.prepare());
        assertTrue(stream.startPlayback("active-call", List.of(new byte[160])));
        listener.get().onText(socket, "{\"event\":\"stream_start\",\"call_id\":\"active-call\",\"encoding\":\"mulaw\",\"sample_rate\":8000}", true);
        assertTrue(stream.hangup("active-call"));

        verify(socket, timeout(1000).atLeastOnce()).sendText(
                org.mockito.ArgumentMatchers.argThat(payload -> payload.toString().contains("\"action\":\"hangup\"")),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void treatsAlreadyEndedRestCallAsIdempotentHangup() {
        DotVoiceHttpClient http = mock(DotVoiceHttpClient.class);
        when(http.hangupCall(any(), org.mockito.ArgumentMatchers.eq("ended-call")))
                .thenReturn(new DotVoiceHttpResponse(404, "already ended"));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(
                properties(500), http, mock(HttpClient.class), new ObjectMapper(), List.of());

        assertTrue(stream.hangup("ended-call"));
    }

    @Test
    void routesIncomingCallWithoutAnswerControlAndUsesDocumentedFields() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(String.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(CompletableFuture.completedFuture(socket));
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        var handler = mock(DotVoiceInboundMediaHandler.class);
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(
                properties(500), null, client, new ObjectMapper(), List.of(handler));

        assertTrue(stream.prepare());
        listener.get().onText(socket, "{\"event\":\"incoming_call\",\"call_id\":\"in-1\",\"from_number\":\"0917\",\"to_number\":\"0091DID\",\"vendor\":\"dotvoice\"}", true);
        verify(handler).onIncomingCall("in-1", "0917", "0091DID");
        verify(socket, org.mockito.Mockito.after(300).never()).sendText(
                org.mockito.ArgumentMatchers.argThat(payload -> payload.toString().contains("\"action\":\"answer\"")),
                org.mockito.ArgumentMatchers.eq(true));

        listener.get().onText(socket, "{\"event\":\"stream_start\",\"call_id\":\"in-1\",\"direction\":\"inbound\",\"encoding\":\"mulaw\",\"sample_rate\":8000}", true);
        verify(handler).onStreamStart("in-1");
    }

    @Test
    void supportsLegacyIncomingCallFieldNamesAndRejectIncludesCallId() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(String.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(CompletableFuture.completedFuture(socket));
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        var handler = mock(DotVoiceInboundMediaHandler.class);
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(
                properties(500), null, client, new ObjectMapper(), List.of(handler));

        assertTrue(stream.prepare());
        listener.get().onText(socket, "{\"event\":\"incoming_call\",\"call_id\":\"in-2\",\"from\":\" 0918 \",\"to\":\"0091DID2\"}", true);
        verify(handler).onIncomingCall("in-2", " 0918 ", "0091DID2");
        assertTrue(stream.rejectCall("in-2"));
        verify(socket, timeout(1000)).sendText(
                org.mockito.ArgumentMatchers.argThat(payload -> payload.toString().contains("\"event\":\"reject_call\"")
                        && payload.toString().contains("\"call_id\":\"in-2\"")),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void sequentialInboundCallsReuseOneSocketAndMatchEachCallIndependently() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        var handler = mock(DotVoiceInboundMediaHandler.class);
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(
                properties(500), null, client, new ObjectMapper(), List.of(handler));

        assertTrue(stream.prepare());
        listener.get().onText(socket, "{\"event\":\"incoming_call\",\"call_id\":\"call-a\",\"from_number\":\"a\",\"to_number\":\"did\"}", true);
        listener.get().onText(socket, "{\"event\":\"stream_start\",\"call_id\":\"call-a\",\"direction\":\"inbound\"}", true);
        listener.get().onText(socket, "{\"event\":\"stream_stop\",\"call_id\":\"call-a\"}", true);
        listener.get().onText(socket, "{\"event\":\"incoming_call\",\"call_id\":\"call-b\",\"from_number\":\"b\",\"to_number\":\"did\"}", true);
        listener.get().onText(socket, "{\"event\":\"stream_start\",\"call_id\":\"call-b\",\"direction\":\"inbound\"}", true);

        verify(handler).onStreamStart("call-a");
        verify(handler).onStreamStart("call-b");
        verify(builder(client), timeout(1000).times(1)).buildAsync(any(URI.class), any(WebSocket.Listener.class));
    }

    private WebSocket.Builder builder(HttpClient client) {
        return client.newWebSocketBuilder();
    }

    @Test
    void rejectsConcurrentPlaybackForTheSameCall() throws Exception {
        WebSocket socket = mock(WebSocket.class);
        when(socket.sendText(any(String.class), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(new CompletableFuture<>());
        AtomicReference<WebSocket.Listener> listener = new AtomicReference<>();
        HttpClient client = client(socket, listener, (l, s) -> l.onText(s, "{\"event\":\"ready\"}", true));
        DefaultDotVoiceMediaStreamClient stream = new DefaultDotVoiceMediaStreamClient(properties(500), client, new ObjectMapper());

        assertTrue(stream.prepare());
        assertTrue(stream.startPlayback("same-call", List.of(new byte[160])));
        listener.get().onText(socket, "{\"event\":\"stream_start\",\"call_id\":\"same-call\",\"encoding\":\"mulaw\",\"sample_rate\":8000}", true);
        assertFalse(stream.startPlayback("same-call", List.of(new byte[160])));
    }

    private HttpClient client(WebSocket socket, AtomicReference<WebSocket.Listener> listener, Callback callback) {
        HttpClient client = mock(HttpClient.class);
        WebSocket.Builder builder = mock(WebSocket.Builder.class);
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any(Duration.class))).thenReturn(builder);
        when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class))).thenAnswer(invocation -> {
            WebSocket.Listener value = invocation.getArgument(1);
            listener.set(value);
            value.onOpen(socket);
            callback.accept(value, socket);
            return CompletableFuture.completedFuture(socket);
        });
        return client;
    }

    private DotVoiceProperties properties(int timeoutMs) {
        DotVoiceProperties properties = new DotVoiceProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setStreamEnabled(true);
        properties.setStreamUrl("ws://stream.test/vendor/stream");
        properties.setStreamTimeoutMs(timeoutMs);
        return properties;
    }

    private byte[] frame(int value) {
        byte[] frame = new byte[160];
        java.util.Arrays.fill(frame, (byte) value);
        return frame;
    }

    @FunctionalInterface
    private interface Callback {
        void accept(WebSocket.Listener listener, WebSocket socket);
    }
}
