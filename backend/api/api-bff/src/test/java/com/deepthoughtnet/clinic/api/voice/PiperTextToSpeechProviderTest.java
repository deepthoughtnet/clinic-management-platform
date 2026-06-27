package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.deepthoughtnet.clinic.api.voice.spi.VoiceSynthesisRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PiperTextToSpeechProviderTest {

    @Test
    void synthesizeReturnsWaveAudioFromLocalService() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getTts().getPiper().setBaseUrl("http://piper-tts:8001");
        properties.getTts().getPiper().setEnglishVoice("en_US-lessac-medium");
        PiperTextToSpeechProvider provider = new PiperTextToSpeechProvider(properties, new RestTemplateBuilder());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://piper-tts:8001/synthesize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Hello there\",\"voice\":\"en_US-lessac-medium\"}"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Hello there", "en"));

        assertThat(result.providerName()).isEqualTo("piper");
        assertThat(result.contentType()).isEqualTo("audio/wav");
        assertThat(result.audioBytes()).isEqualTo("wav-audio".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void synthesizeThrowsFriendlyMessageWhenServiceReturnsEmptyAudio() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getTts().getPiper().setBaseUrl("http://piper-tts:8001");
        PiperTextToSpeechProvider provider = new PiperTextToSpeechProvider(properties, new RestTemplateBuilder());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://piper-tts:8001/synthesize"))
                .andRespond(withSuccess(new byte[0], MediaType.parseMediaType("audio/wav")));

        assertThatThrownBy(() -> provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Hello there", "en")))
                .hasMessageContaining("Audio could not be synthesized");
        server.verify();
    }

    @Test
    void synthesizeUsesConfiguredHindiVoiceWhenRequested() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getTts().getPiper().setBaseUrl("http://piper-tts:8001");
        properties.getTts().getPiper().setEnglishVoice("en_US-lessac-medium");
        properties.getTts().getPiper().setHindiVoice("hi_IN-rohan-medium");
        PiperTextToSpeechProvider provider = new PiperTextToSpeechProvider(properties, new RestTemplateBuilder());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://piper-tts:8001/synthesize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"voice\":\"hi_IN-rohan-medium\"}"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "hi"));

        assertThat(result.providerName()).isEqualTo("piper");
        server.verify();
    }

    @Test
    void synthesizeUsesConfiguredHindiDefaultVoiceAndFallsBackOnVoiceNotFound() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getTts().getPiper().setBaseUrl("http://piper-tts:8001");
        properties.getTts().getPiper().setEnglishVoice("en_US-lessac-medium");
        properties.getTts().getPiper().setAllowFallbackVoice(true);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("PIPER_DEFAULT_VOICE", "supertonic-3-hi")
                .withProperty("PIPER_FALLBACK_VOICE", "hi_IN-rohan-medium");
        PiperTextToSpeechProvider provider = new PiperTextToSpeechProvider(properties, new RestTemplateBuilder(), environment);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://piper-tts:8001/synthesize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"voice\":\"supertonic-3-hi\"}"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("voice-not-found"));
        server.expect(once(), requestTo("http://piper-tts:8001/synthesize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"voice\":\"hi_IN-rohan-medium\"}"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "hi"));

        assertThat(result.providerName()).isEqualTo("piper");
        assertThat(result.contentType()).isEqualTo("audio/wav");
        server.verify();
    }

    @Test
    void synthesizeFallsBackToEnglishVoiceWhenHindiVoiceIsMissing() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getTts().getPiper().setBaseUrl("http://piper-tts:8001");
        properties.getTts().getPiper().setEnglishVoice("en_US-lessac-medium");
        properties.getTts().getPiper().setAllowFallbackVoice(true);
        PiperTextToSpeechProvider provider = new PiperTextToSpeechProvider(properties, new RestTemplateBuilder());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://piper-tts:8001/synthesize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"text\":\"Namaste\",\"voice\":\"en_US-lessac-medium\"}"))
                .andRespond(withSuccess("wav-audio".getBytes(StandardCharsets.UTF_8), MediaType.parseMediaType("audio/wav")));

        var result = provider.synthesize(new VoiceSynthesisRequest(UUID.randomUUID(), "Namaste", "hi"));

        assertThat(result.providerName()).isEqualTo("piper");
        assertThat(provider.isLanguageVoiceConfigured("hi")).isFalse();
        server.verify();
    }
}
