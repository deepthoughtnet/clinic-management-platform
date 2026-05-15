package com.deepthoughtnet.clinic.api.realtime.websocket;

import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventBus;
import com.deepthoughtnet.clinic.realtime.voice.metrics.RealtimeVoiceGatewayMetrics;
import com.deepthoughtnet.clinic.realtime.voice.session.RealtimeVoiceSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Websocket endpoint wiring for realtime voice session event stream foundation. */
@Configuration
@EnableWebSocket
public class VoiceWebSocketConfig implements WebSocketConfigurer {
    private final VoiceSessionEventBus eventBus;
    private final RealtimeVoiceGatewayMetrics metrics;
    private final ObjectMapper objectMapper;
    private final VoiceWebSocketAuthInterceptor authInterceptor;
    private final RealtimeVoiceSessionService sessionService;

    public VoiceWebSocketConfig(VoiceSessionEventBus eventBus, RealtimeVoiceGatewayMetrics metrics,
                                ObjectMapper objectMapper, VoiceWebSocketAuthInterceptor authInterceptor,
                                RealtimeVoiceSessionService sessionService) {
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.authInterceptor = authInterceptor;
        this.sessionService = sessionService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new VoiceSessionWebSocketHandler(eventBus, metrics, objectMapper, sessionService), "/ws/voice/session/*")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
    }
}
