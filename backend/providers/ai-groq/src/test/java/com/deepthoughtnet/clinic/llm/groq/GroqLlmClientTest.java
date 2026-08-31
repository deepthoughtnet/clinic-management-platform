package com.deepthoughtnet.clinic.llm.groq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepthoughtnet.clinic.llm.spi.AiProviderException;
import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.deepthoughtnet.clinic.llm.spi.LlmResponse;
import com.deepthoughtnet.clinic.platform.contracts.ai.AiTaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GroqLlmClientTest {
    private static final String BASE_URL = "https://api.groq.com/openai/v1";
    private static final String MODEL = "openai/gpt-oss-20b";

    private RestClient capturedRestClient;
    private RestClient.RequestBodyUriSpec capturedPostSpec;

    @Test
    void validatesConfiguredModelFromGroqCatalog() {
        GroqLlmClient client = clientWithCatalog("""
                {"data":[{"id":"openai/gpt-oss-20b"},{"id":"meta/llama-3.1-70b-instruct"}]}
                """);

        assertThat(client.isAvailable()).isTrue();
        assertThat(client.availabilityDiagnostic()).contains("provider=GROQ");
        assertThat(client.availabilityDiagnostic()).contains("model=" + MODEL);
    }

    @Test
    void marksConfiguredModelUnavailableWhenGroqCatalogDoesNotContainIt() {
        GroqLlmClient client = clientWithCatalog("""
                {"data":[{"id":"other-model"}]}
                """);

        assertThat(client.isAvailable()).isFalse();
        assertThat(client.availabilityDiagnostic()).contains("model_not_found");
    }

    @Test
    void generateUsesValidatedGroqModelInPayload() throws Exception {
        GroqLlmClient client = clientWithCatalogAndCompletion("""
                {
                  "choices":[{"message":{"content":"{\\"answer\\":\\"Groq fallback answer\\"}"},"finish_reason":"stop"}],
                  "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}
                }
                """);

        LlmResponse response = client.generate(new LlmRequest(
                "system",
                "user",
                null,
                null,
                null,
                null,
                null,
                AiTaskType.CLINICAL_REASONING,
                null,
                null,
                true
        ));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(capturedRestClient).post();
        verify(capturedPostSpec).body(payloadCaptor.capture());
        String requestJson = new ObjectMapper().writeValueAsString(payloadCaptor.getValue());
        assertThat(requestJson).contains("\"model\":\"" + MODEL + "\"");
        assertThat(requestJson).contains("\"temperature\":0.1");
        assertThat(requestJson).contains("\"max_tokens\":2048");
        assertThat(response.provider()).isEqualTo("GROQ");
        assertThat(response.model()).isEqualTo(MODEL);
        assertThat(response.text()).contains("Groq fallback answer");
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(15L);
    }

    @Test
    void failsFastWhenGroqCatalogDoesNotContainConfiguredModel() {
        GroqLlmClient client = clientWithCatalog("""
                {"data":[{"id":"other-model"}]}
                """);

        assertThatThrownBy(() -> client.generate(new LlmRequest(
                "system",
                "user",
                null,
                null,
                null,
                null,
                null,
                AiTaskType.CLINICAL_REASONING,
                null,
                null,
                true
        ))).isInstanceOf(AiProviderException.class)
                .hasMessageContaining("model_not_found");
    }

    private GroqLlmClient clientWithCatalog(String modelsBody) {
        capturedRestClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class, RETURNS_SELF);
        RestClient.RequestHeadersSpec getHeadersSpec = mock(RestClient.RequestHeadersSpec.class, RETURNS_SELF);
        RestClient.ResponseSpec getResponseSpec = mock(RestClient.ResponseSpec.class);

        when(capturedRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(BASE_URL + "/models")).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);
        when(getResponseSpec.body(String.class)).thenReturn(modelsBody);

        return new GroqLlmClient(
                new ObjectMapper(),
                capturedRestClient,
                BASE_URL,
                "groq-api-key",
                MODEL,
                0.1d,
                2048,
                60
        );
    }

    private GroqLlmClient clientWithCatalogAndCompletion(String completionBody) {
        capturedRestClient = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec getSpec = mock(RestClient.RequestHeadersUriSpec.class, RETURNS_SELF);
        RestClient.RequestHeadersSpec getHeadersSpec = mock(RestClient.RequestHeadersSpec.class, RETURNS_SELF);
        RestClient.ResponseSpec getResponseSpec = mock(RestClient.ResponseSpec.class);
        capturedPostSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
        RestClient.ResponseSpec postResponseSpec = mock(RestClient.ResponseSpec.class);

        when(capturedRestClient.get()).thenReturn(getSpec);
        when(getSpec.uri(BASE_URL + "/models")).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getResponseSpec);
        when(getResponseSpec.body(String.class)).thenReturn("""
                {"data":[{"id":"openai/gpt-oss-20b"}]}
                """);

        when(capturedRestClient.post()).thenReturn(capturedPostSpec);
        when(capturedPostSpec.uri(BASE_URL + "/chat/completions")).thenReturn(capturedPostSpec);
        when(capturedPostSpec.body(any())).thenReturn(capturedPostSpec);
        when(capturedPostSpec.retrieve()).thenReturn(postResponseSpec);
        when(postResponseSpec.body(String.class)).thenReturn(completionBody);

        return new GroqLlmClient(
                new ObjectMapper(),
                capturedRestClient,
                BASE_URL,
                "groq-api-key",
                MODEL,
                0.1d,
                2048,
                60
        );
    }
}
