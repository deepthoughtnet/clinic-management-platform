package com.deepthoughtnet.clinic.api.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deepthoughtnet.clinic.api.voice.spi.VoiceTranscriptionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FasterWhisperSpeechToTextProviderTest {

    @Test
    void transcribeReturnsTranscriptFromLocalService() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().getFasterWhisper().setBaseUrl("http://faster-whisper:8000");
        FasterWhisperSpeechToTextProvider provider = new FasterWhisperSpeechToTextProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://faster-whisper:8000/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("multipart/form-data")))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    String body = httpRequest.getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body).contains("name=\"file\"");
                    assertThat(body).contains("filename=\"sample.webm\"");
                    assertThat(body).contains("Content-Type: audio/webm");
                    assertThat(body).contains("name=\"language\"");
                    assertThat(body).contains("name=\"model\"");
                })
                .andRespond(withSuccess("{\"text\":\"Hello from local whisper\"}", MediaType.APPLICATION_JSON));

        var result = provider.transcribe(new VoiceTranscriptionRequest(
                UUID.randomUUID(),
                "audio".getBytes(StandardCharsets.UTF_8),
                "audio/webm",
                "sample.webm",
                "en"
        ));

        assertThat(result.transcript()).isEqualTo("Hello from local whisper");
        assertThat(result.providerName()).isEqualTo("FASTER_WHISPER");
        server.verify();
    }

    @Test
    void transcribeThrowsFriendlyMessageWhenTranscriptMissing() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().getFasterWhisper().setBaseUrl("http://faster-whisper:8000");
        FasterWhisperSpeechToTextProvider provider = new FasterWhisperSpeechToTextProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://faster-whisper:8000/transcribe"))
                .andRespond(withSuccess("{\"text\":\"\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.transcribe(new VoiceTranscriptionRequest(
                UUID.randomUUID(),
                "audio".getBytes(StandardCharsets.UTF_8),
                "audio/webm",
                "sample.webm",
                "en"
        ))).hasMessageContaining("Audio could not be transcribed");
        server.verify();
    }

    @Test
    void transcribeSupportsWavUploads() {
        VoiceTestProperties properties = new VoiceTestProperties();
        properties.getStt().getFasterWhisper().setBaseUrl("http://faster-whisper:8000");
        FasterWhisperSpeechToTextProvider provider = new FasterWhisperSpeechToTextProvider(properties, new RestTemplateBuilder(), new ObjectMapper());
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(provider, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo("http://faster-whisper:8000/transcribe"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.containsString("multipart/form-data")))
                .andExpect(request -> {
                    MockClientHttpRequest httpRequest = (MockClientHttpRequest) request;
                    String body = httpRequest.getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body).contains("name=\"file\"");
                    assertThat(body).contains("filename=\"sample.wav\"");
                    assertThat(body).contains("Content-Type: audio/wav");
                })
                .andRespond(withSuccess("{\"text\":\"Hello from wav\"}", MediaType.APPLICATION_JSON));

        var result = provider.transcribe(new VoiceTranscriptionRequest(
                UUID.randomUUID(),
                "audio".getBytes(StandardCharsets.UTF_8),
                "audio/wav",
                "sample.wav",
                "en"
        ));

        assertThat(result.transcript()).isEqualTo("Hello from wav");
        assertThat(result.providerName()).isEqualTo("FASTER_WHISPER");
        server.verify();
    }
}
