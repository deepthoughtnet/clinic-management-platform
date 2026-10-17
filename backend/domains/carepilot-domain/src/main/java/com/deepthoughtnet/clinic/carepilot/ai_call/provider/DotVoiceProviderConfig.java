package com.deepthoughtnet.clinic.carepilot.ai_call.provider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DotVoiceProperties.class)
public class DotVoiceProviderConfig {
    @Bean
    public DotVoiceHttpClient dotVoiceHttpClient() { return new DefaultDotVoiceHttpClient(); }

    @Bean
    public DotVoiceMediaStreamClient dotVoiceMediaStreamClient(DotVoiceProperties properties,
                                                               DotVoiceHttpClient httpClient,
                                                               java.util.List<DotVoiceInboundMediaHandler> inboundHandlers) {
        return new DefaultDotVoiceMediaStreamClient(properties, httpClient,
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build(),
                new com.fasterxml.jackson.databind.ObjectMapper(), inboundHandlers);
    }

    @Bean
    public DotVoiceVoiceCallProvider dotVoiceVoiceCallProvider(DotVoiceProperties properties, DotVoiceHttpClient client) {
        return new DotVoiceVoiceCallProvider(properties, client);
    }
}
