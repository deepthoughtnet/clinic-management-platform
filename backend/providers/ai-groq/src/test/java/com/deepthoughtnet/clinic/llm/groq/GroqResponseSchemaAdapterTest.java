package com.deepthoughtnet.clinic.llm.groq;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroqResponseSchemaAdapterTest {
    @Test
    void addsRecursiveRequiredAndNullableOptionalFields() throws Exception {
        Map<String, Object> logical = new LinkedHashMap<>();
        logical.put("type", "object");
        logical.put("properties", Map.of(
                "operation", Map.of("type", "string", "enum", List.of("START_BOOKING")),
                "confirmation", Map.of("type", "string", "enum", List.of("NONE", "POSITIVE")),
                "selection", Map.of("type", "object", "properties", Map.of(
                        "ordinal", Map.of("type", "integer")))));

        Map<String, Object> adapted = GroqResponseSchemaAdapter.adapt(logical);
        String json = new ObjectMapper().writeValueAsString(adapted);

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) adapted.get("required");
        assertThat(required)
                .containsExactlyInAnyOrder("operation", "confirmation", "selection");
        Map<?, ?> selection = (Map<?, ?>) ((Map<?, ?>) adapted.get("properties")).get("selection");
        @SuppressWarnings("unchecked")
        List<String> selectionRequired = (List<String>) selection.get("required");
        assertThat(selectionRequired).containsExactly("ordinal");
        assertThat(json).contains("\"operation\":{\"type\":\"string\"");
        assertThat(json).contains("\"confirmation\":{\"type\":[\"string\",\"null\"]");
        assertThat(json).contains("\"ordinal\":{\"type\":[\"integer\",\"null\"]");
        assertThat(json).contains("\"enum\":[\"NONE\",\"POSITIVE\",null]");
    }

    @Test
    void preservesLogicalSchemaInput() {
        Map<String, Object> logical = new LinkedHashMap<>();
        logical.put("type", "object");
        logical.put("properties", Map.of("mode", Map.of("type", "string", "enum", List.of("SET"))));

        Map<String, Object> adapted = GroqResponseSchemaAdapter.adapt(logical);

        assertThat(logical).doesNotContainKey("required");
        assertThat(adapted).containsKey("required");
        assertThat(adapted.get("properties").toString()).contains("mode");
    }
}
