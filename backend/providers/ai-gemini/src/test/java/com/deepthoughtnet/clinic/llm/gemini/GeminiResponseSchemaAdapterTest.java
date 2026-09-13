package com.deepthoughtnet.clinic.llm.gemini;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeminiResponseSchemaAdapterTest {
    @Test
    void removesUnsupportedAdditionalPropertiesRecursivelyWithoutDroppingContractFields() throws Exception {
        Map<String, Object> logicalSchema = new LinkedHashMap<>();
        logicalSchema.put("type", "object");
        logicalSchema.put("additionalProperties", false);
        logicalSchema.put("minimum", 0);
        logicalSchema.put("maximum", 1);
        logicalSchema.put("properties", Map.of(
                "operation", Map.of("type", "string", "enum", List.of("START_BOOKING")),
                "bookingPatch", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", Map.of("dateExpression", Map.of("type", "string")))));

        String adapted = new ObjectMapper().writeValueAsString(GeminiResponseSchemaAdapter.adapt(logicalSchema));

        assertThat(adapted).doesNotContain("additionalProperties");
        assertThat(adapted).doesNotContain("minimum");
        assertThat(adapted).doesNotContain("maximum");
        assertThat(adapted).contains("operation");
        assertThat(adapted).contains("bookingPatch");
        assertThat(adapted).contains("dateExpression");
        assertThat(adapted).contains("START_BOOKING");
    }
}
