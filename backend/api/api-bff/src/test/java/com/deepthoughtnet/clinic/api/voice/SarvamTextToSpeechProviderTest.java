package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SarvamTextToSpeechProviderTest {

    @Test
    void isNotReadyWhenDisabled() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(false);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("secret");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper());

        assertThat(provider.isReady()).isFalse();
    }

    @Test
    void synthesizeReturnsPlayableAudioFromSarvamService() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setTtsPath("/text-to-speech");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/text-to-speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"language\":\"hi-IN\"}"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "auto"));

        assertThat(result.providerName()).isEqualTo("sarvam");
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.audioBytes()).isEqualTo("wav-audio".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void synthesizeUsesSarvamSpeakerFromEnvWhenConfigured() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setTtsPath("/text-to-speech");
        MockEnvironment environment = new MockEnvironment().withProperty("SARVAM_TTS_SPEAKER", "shubh");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper(), environment);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/text-to-speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"language\":\"hi-IN\",\"speaker\":\"shubh\"}"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "auto"));

        assertThat(result.providerName()).isEqualTo("sarvam");
        server.verify();
    }

    @Test
    void synthesizeFallsBackToAivaDefaultVoiceWhenSarvamSpeakerIsMissing() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setTtsPath("/text-to-speech");
        MockEnvironment environment = new MockEnvironment().withProperty("AIVA_DEFAULT_VOICE", "shubh");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper(), environment);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/text-to-speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"language\":\"hi-IN\",\"speaker\":\"shubh\"}"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "auto"));

        assertThat(result.providerName()).isEqualTo("sarvam");
        server.verify();
    }

    @Test
    void synthesizeDecodesBase64AudioFromJsonResponse() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setTtsPath("/text-to-speech");
        properties.setResponseLanguage("hi-IN");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/text-to-speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"language\":\"hi-IN\"}"))
                .andRespond(withSuccess("{\"audioBase64\":\"" + Base64.getEncoder().encodeToString("json-audio".getBytes(StandardCharsets.UTF_8)) + "\"}", MediaType.APPLICATION_JSON));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "en"));

        assertThat(result.providerName()).isEqualTo("sarvam");
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.audioBytes()).isEqualTo("json-audio".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void synthesizeDecodesBase64AudioFromNestedDataAudioResponse() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setTtsPath("/text-to-speech");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/text-to-speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"language\":\"hi-IN\"}"))
                .andRespond(withSuccess("{\"data\":{\"audioContent\":\"" + Base64.getEncoder().encodeToString("nested-audio".getBytes(StandardCharsets.UTF_8)) + "\"}}", MediaType.APPLICATION_JSON));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "hi-IN"));

        assertThat(result.providerName()).isEqualTo("sarvam");
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.audioBytes()).isEqualTo("nested-audio".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void synthesizeDecodesBase64AudioFromArrayResponse() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setTtsPath("/text-to-speech");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/text-to-speech"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"language\":\"hi-IN\"}"))
                .andRespond(withSuccess("[{\"audio\":\"" + Base64.getEncoder().encodeToString("array-audio".getBytes(StandardCharsets.UTF_8)) + "\"}]", MediaType.APPLICATION_JSON));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "en"));

        assertThat(result.providerName()).isEqualTo("sarvam");
        assertThat(result.contentType()).isEqualTo("audio/mpeg");
        assertThat(result.audioBytes()).isEqualTo("array-audio".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void apiKeyIsNotLogged() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getSarvam().setEnabled(true);
        properties.getSarvam().setTtsEnabled(true);
        properties.getSarvam().setApiKey("super-secret-key");
        properties.getSarvam().setBaseUrl("http://sarvam.test");
        properties.getSarvam().setTtsPath("/text-to-speech");
        SarvamTextToSpeechProvider provider = new SarvamTextToSpeechProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://sarvam.test/text-to-speech"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        Logger logger = (Logger) LoggerFactory.getLogger(SarvamTextToSpeechProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "auto"));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("super-secret-key"));
        server.verify();
    }
}
