package com.deepthoughtnet.clinic.api.realtime.websocket;

import com.deepthoughtnet.clinic.api.voice.VoiceOrchestratorService;
import com.deepthoughtnet.clinic.api.voice.VoiceTestProperties;
import com.deepthoughtnet.clinic.api.voice.VoiceTestWebSocketHandler;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceAssistantService;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceWebSocketAuthInterceptor;
import com.deepthoughtnet.clinic.api.patientportal.voice.PatientPortalVoiceWebSocketHandler;
import com.deepthoughtnet.clinic.realtime.voice.events.VoiceSessionEventBus;
import com.deepthoughtnet.clinic.realtime.voice.metrics.RealtimeVoiceGatewayMetrics;
import com.deepthoughtnet.clinic.realtime.voice.session.RealtimeVoiceSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
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
    private final VoiceOrchestratorService voiceOrchestratorService;
    private final VoiceTestProperties voiceTestProperties;
    private final PatientPortalVoiceWebSocketAuthInterceptor patientPortalVoiceAuthInterceptor;
    private final PatientPortalVoiceAssistantService patientPortalVoiceAssistantService;

    public VoiceWebSocketConfig(VoiceSessionEventBus eventBus, RealtimeVoiceGatewayMetrics metrics,
                                ObjectMapper objectMapper, VoiceWebSocketAuthInterceptor authInterceptor,
                                RealtimeVoiceSessionService sessionService,
                                VoiceOrchestratorService voiceOrchestratorService,
                                VoiceTestProperties voiceTestProperties,
                                PatientPortalVoiceWebSocketAuthInterceptor patientPortalVoiceAuthInterceptor,
                                PatientPortalVoiceAssistantService patientPortalVoiceAssistantService) {
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.authInterceptor = authInterceptor;
        this.sessionService = sessionService;
        this.voiceOrchestratorService = voiceOrchestratorService;
        this.voiceTestProperties = voiceTestProperties;
        this.patientPortalVoiceAuthInterceptor = patientPortalVoiceAuthInterceptor;
        this.patientPortalVoiceAssistantService = patientPortalVoiceAssistantService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new VoiceSessionWebSocketHandler(eventBus, metrics, objectMapper, sessionService), "/ws/voice/session/*")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(new VoiceTestWebSocketHandler(objectMapper, voiceOrchestratorService, voiceTestProperties), "/ws/voice/test")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins("*");
        registry.addHandler(new PatientPortalVoiceWebSocketHandler(objectMapper, patientPortalVoiceAssistantService, voiceTestProperties), "/ws/patient-portal/careai")
                .addInterceptors(patientPortalVoiceAuthInterceptor)
                .setAllowedOrigins("*");
    }

    @Bean
    ServletServerContainerFactoryBean voiceWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(256 * 1024);
        container.setMaxBinaryMessageBufferSize(256 * 1024);
        return container;
    }
}
