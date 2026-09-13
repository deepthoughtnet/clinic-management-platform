package com.deepthoughtnet.clinic.llm.sarvam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.deepthoughtnet.clinic.llm.spi.LlmRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SarvamLlmClientTest {
    @Test
    void semanticAvailabilityDoesNotDependOnSttCredentialWhenSemanticKeyIsMissing() {
        SarvamLlmClient client = new SarvamLlmClient(new ObjectMapper(), RestClient.builder().build(),
                "https://api.sarvam.ai", "", "sarvam-105b-conversations", 0.1, 512, 5);

        assertThat(client.isAvailable()).isFalse();
        assertThat(client.availabilityDiagnostic()).contains("not configured");
    }

    @Test
    void callsSemanticChatEndpointWithSarvamCredentialsAndReturnsContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SarvamLlmClient client = new SarvamLlmClient(new ObjectMapper(), builder.build(),
                "https://api.sarvam.ai", "test-key", "sarvam-105b-conversations", 0.1, 512, 5);

        server.expect(requestTo("https://api.sarvam.ai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-subscription-key", "test-key"))
                .andRespond(withSuccess("""
                        {"model":"sarvam-105b-conversations","choices":[{"message":{"content":"{\\"operation\\":\\"LOOKUP_APPOINTMENTS\\"}"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        var response = client.generate(new LlmRequest("system", "meri next appointment kab hai", null, null,
                null, null, null, null, null, null, true, Map.of("type", "object")));

        assertThat(response.provider()).isEqualTo("SARVAM");
        assertThat(response.text()).contains("LOOKUP_APPOINTMENTS");
        server.verify();
    }

    @Test
    void sendsSarvamSpecificStrictJsonSchemaContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SarvamLlmClient client = new SarvamLlmClient(new ObjectMapper(), builder.build(),
                "https://api.sarvam.ai", "test-key", "sarvam-105b-conversations", 0.1, 512, 5);

        server.expect(request -> {
            JsonNode payload = new ObjectMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
            JsonNode format = payload.path("response_format");
            assertThat(format.path("type").asText()).isEqualTo("json_schema");
            assertThat(format.path("json_schema").path("strict").asBoolean()).isTrue();
            assertThat(format.path("json_schema").path("schema").path("required").toString())
                    .isEqualTo("[\"schemaVersion\",\"dialogAct\",\"operation\"]");
            assertThat(format.path("json_schema").path("schema").path("properties")
                    .path("responseLanguage").path("description").asText())
                    .contains("Exactly one BCP-47");
        }).andRespond(withSuccess("""
                {"model":"sarvam-105b-conversations","choices":[{"message":{"content":"{\\"schemaVersion\\":\\"1.0\\",\\"dialogAct\\":\\"ASK_QUESTION\\",\\"operation\\":\\"LOOKUP_APPOINTMENTS\\"}"}}]}
                """, MediaType.APPLICATION_JSON));

        client.generate(new LlmRequest("system", "lookup", null, null, null, null, 512,
                null, null, null, true, Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "schemaVersion", Map.of("type", "string"),
                                "dialogAct", Map.of("type", "string"),
                                "operation", Map.of("type", "string"),
                                "responseLanguage", Map.of("type", "string")))));

        server.verify();
    }
}
