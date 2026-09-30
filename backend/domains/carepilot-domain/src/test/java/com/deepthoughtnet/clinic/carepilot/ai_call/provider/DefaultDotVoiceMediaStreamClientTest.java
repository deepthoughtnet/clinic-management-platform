package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultDotVoiceMediaStreamClientTest {
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

    @FunctionalInterface
    private interface Callback {
        void accept(WebSocket.Listener listener, WebSocket socket);
    }
}
