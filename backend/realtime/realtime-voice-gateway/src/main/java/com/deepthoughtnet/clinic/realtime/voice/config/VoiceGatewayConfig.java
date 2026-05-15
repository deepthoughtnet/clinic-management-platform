package com.deepthoughtnet.clinic.realtime.voice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables typed configuration for realtime voice gateway runtime knobs.
 */
@Configuration
@EnableConfigurationProperties(VoiceGatewayProperties.class)
public class VoiceGatewayConfig {
    private static final Logger log = LoggerFactory.getLogger(VoiceGatewayConfig.class);

    public VoiceGatewayConfig(VoiceGatewayProperties properties) {
        if (properties.getStt().getEndpoint().isBlank() || properties.getTts().getEndpoint().isBlank()) {
            log.warn("Voice STT/TTS endpoint configuration is blank. Falling back providers may be used.");
        }
    }
}
