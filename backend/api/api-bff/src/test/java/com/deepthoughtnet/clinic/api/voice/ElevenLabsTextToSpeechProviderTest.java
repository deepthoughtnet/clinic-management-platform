package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ElevenLabsTextToSpeechProviderTest {

    @Test
    void synthesizeSendsFixedTextModelAndOutputFormatToConfiguredVoiceEndpoint() {
        ElevenLabsTextToSpeechProvider provider = provider();
        RestTemplate restTemplate = restTemplate(provider);
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://elevenlabs.test/v1/text-to-speech/voice-id"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "text": "Hello, this is the Jeevanam voice test.",
                          "model_id": "eleven_multilingual_v2",
                          "output_format": "mp3_44100_128"
                        }
                        """))
                .andRespond(withSuccess("audio-bytes".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/mpeg")));

        Logger logger = (Logger) LoggerFactory.getLogger(ElevenLabsTextToSpeechProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Hello, this is the Jeevanam voice test.", "en"));

            assertThat(result.providerName()).isEqualTo("elevenlabs");
            assertThat(result.contentType()).isEqualTo("audio/mpeg");
            assertThat(result.audioBytes()).isEqualTo("audio-bytes".getBytes(StandardCharsets.UTF_8));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("voice.tts.elevenlabs.success"))
                .noneMatch(message -> message.contains("super-secret-key"))
                .noneMatch(message -> message.contains("audio-bytes"));
        server.verify();
    }

    @Test
    void synthesizeRejectsBlankTextBeforeNetworkCall() {
        ElevenLabsTextToSpeechProvider provider = provider();

        assertThatThrownBy(() -> provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "   ", "en")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ElevenLabs text is required");
    }

    @ParameterizedTest
    @MethodSource("errorMappings")
    void synthesizeMapsJsonErrorDetailsToSafeCategories(String detailStatus,
                                                        String expectedCategory,
                                                        String expectedMessage) {
        ElevenLabsTextToSpeechProvider provider = provider();
        RestTemplate restTemplate = restTemplate(provider);
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://elevenlabs.test/v1/text-to-speech/voice-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "detail": {
                            "status": "%s"
                          }
                        }
                        """.formatted(detailStatus), MediaType.APPLICATION_JSON));

        Logger logger = (Logger) LoggerFactory.getLogger(ElevenLabsTextToSpeechProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Hello, this is the Jeevanam voice test.", "en")))
                    .isInstanceOf(ElevenLabsTextToSpeechProvider.ElevenLabsTtsException.class)
                    .hasMessage(expectedMessage);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("voice.tts.elevenlabs.failed"))
                .anyMatch(message -> message.contains("status=200"))
                .anyMatch(message -> message.contains("category=" + expectedCategory))
                .noneMatch(message -> message.contains("super-secret-key"))
                .noneMatch(message -> message.contains("detail"))
                .noneMatch(message -> message.contains("voice-id"));
        server.verify();
    }

    private static Stream<Arguments> errorMappings() {
        return Stream.of(
                Arguments.of("invalid_api_key", "AUTHENTICATION_FAILED", "Authentication failed"),
                Arguments.of("api_key_id_used_as_api_key", "AUTHENTICATION_FAILED", "Authentication failed"),
                Arguments.of("voice_not_found", "VOICE_NOT_FOUND", "Voice ID not found or not accessible"),
                Arguments.of("quota_exceeded", "QUOTA", "ElevenLabs quota exceeded"),
                Arguments.of("validation_error", "INVALID_REQUEST", "Invalid model or request"),
                Arguments.of("rate_limit", "RATE_LIMIT", "Rate limit reached"),
                Arguments.of("unexpected", "UNKNOWN", "ElevenLabs request failed")
        );
    }

    private ElevenLabsTextToSpeechProvider provider() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getTts().getElevenlabs().setApiKey("super-secret-key");
        properties.getTts().getElevenlabs().setVoiceId("voice-id");
        properties.getTts().getElevenlabs().setModel("eleven_multilingual_v2");
        properties.getTts().getElevenlabs().setBaseUrl("http://elevenlabs.test/v1/text-to-speech");
        return new ElevenLabsTextToSpeechProvider(properties, new ObjectMapper(), new RestTemplateBuilder());
    }

    private RestTemplate restTemplate(ElevenLabsTextToSpeechProvider provider) {
        return (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
    }
}
