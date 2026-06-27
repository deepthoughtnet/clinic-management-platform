package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.slf4j.LoggerFactory;

class SarvamSpeechToTextProviderTest {

    @Test
    void isNotReadyWhenDisabled() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(false);
        properties.getSarvam().setSttEnabled(true);
        properties.getSarvam().setApiKey("secret");
        SarvamSpeechToTextProvider provider = new SarvamSpeechToTextProvider(properties, new RestTemplateBuilder(), new ObjectMapper());

        assertThat(provider.isReady()).isFalse();
    }

    @Test
    void transcribeReturnsTranscriptFromSarvamService() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setSttEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setSttPath("/speech-to-text");
        SarvamSpeechToTextProvider provider = new SarvamSpeechToTextProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/speech-to-text?language=hi-IN"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    String body = httpRequest.getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body).contains("name=\"file\"");
                    assertThat(body).contains("filename=\"sample.webm\"");
                    assertThat(body).contains("Content-Type: audio/webm");
                    assertThat(body).doesNotContain("audio/webm;codecs=opus");
                    assertThat(body).contains("name=\"language\"");
                    assertThat(body).contains("hi-IN");
                })
                .andRespond(withSuccess("{\"text\":\"Namaste from Sarvam\"}", MediaType.APPLICATION_JSON));

        var result = provider.transcribe(new VoiceTranscriptionRequest(
                UUID.randomUUID(),
                "audio".getBytes(StandardCharsets.UTF_8),
                "audio/webm;codecs=opus",
                "sample.webm",
                "auto"
        ));

        assertThat(result.providerName()).isEqualTo("sarvam");
        assertThat(result.transcript()).isEqualTo("Namaste from Sarvam");
        server.verify();
    }

    @Test
    void transcribeNormalizesWebmCodecContentTypeForSarvam() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setSttEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setSttPath("/speech-to-text");
        SarvamSpeechToTextProvider provider = new SarvamSpeechToTextProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/speech-to-text?language=hi-IN"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    String body = httpRequest.getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body).contains("Content-Type: audio/webm");
                    assertThat(body).doesNotContain("codecs=opus");
                })
                .andRespond(withSuccess("{\"text\":\"Hello\"}", MediaType.APPLICATION_JSON));

        Logger logger = (Logger) LoggerFactory.getLogger(SarvamSpeechToTextProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            provider.transcribe(new VoiceTranscriptionRequest(
                    UUID.randomUUID(),
                    "audio".getBytes(StandardCharsets.UTF_8),
                    "audio/webm;codecs=opus",
                    "sample.webm",
                    "auto"
            ));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("originalContentType=audio/webm;codecs=opus"))
                .anyMatch(message -> message.contains("sarvamContentType=audio/webm"));
        server.verify();
    }

    @Test
    void apiKeyIsNotLogged() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setSttEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setSttPath("/speech-to-text");
        SarvamSpeechToTextProvider provider = new SarvamSpeechToTextProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/speech-to-text?language=hi-IN"))
                .andRespond(withSuccess("{\"text\":\"Hello\"}", MediaType.APPLICATION_JSON));

        Logger logger = (Logger) LoggerFactory.getLogger(SarvamSpeechToTextProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            provider.transcribe(new VoiceTranscriptionRequest(
                    UUID.randomUUID(),
                    "audio".getBytes(StandardCharsets.UTF_8),
                    "audio/webm",
                    "sample.webm",
                    "auto"
            ));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("super-secret-key"));
        server.verify();
    }
}
