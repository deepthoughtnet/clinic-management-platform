package com.deepthoughtnet.clinic.llm.sarvam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SarvamResponseSchemaAdapterTest {
    @Test
    void requiresAivaCoreAndConstrainsResponseLanguageToOneTag() {
        Map<String, Object> adapted = SarvamResponseSchemaAdapter.adapt(Map.of(
                "type", "object",
                "properties", Map.of(
                        "schemaVersion", Map.of("type", "string"),
                        "dialogAct", Map.of("type", "string"),
                        "operation", Map.of("type", "string"),
                        "responseLanguage", Map.of("type", "string"),
                        "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1))));

        assertThat(adapted.get("required")).isEqualTo(List.of("schemaVersion", "dialogAct", "operation"));
        @SuppressWarnings("unchecked")
        Map<String, Object> language = (Map<String, Object>) ((Map<?, ?>) adapted.get("properties")).get("responseLanguage");
        assertThat(language).containsEntry("type", "string");
        assertThat(language.get("description").toString()).contains("Exactly one BCP-47");
        assertThat(language.get("description").toString()).doesNotContain(",auto-detect");
        assertThat(((Map<?, ?>) adapted.get("properties")).get("confidence")).isEqualTo(
                Map.of("type", "number", "minimum", 0, "maximum", 1));
    }
}
