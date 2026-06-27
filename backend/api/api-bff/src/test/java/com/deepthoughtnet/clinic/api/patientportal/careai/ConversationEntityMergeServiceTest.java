package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationEntityMergeServiceTest {
    private final ConversationEntityMergeService service = new ConversationEntityMergeService();

    @Test
    void latestExplicitValuesWinWithoutErasingExistingValues() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("doctorName", "Dr Ashish Shri");
        current.put("preferredDate", "2026-06-30");

        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("preferredTimeWindow", "evening");
        incoming.put("preferredDate", null);

        Map<String, Object> merged = service.merge(current, incoming);

        assertThat(merged).containsEntry("doctorName", "Dr Ashish Shri");
        assertThat(merged).containsEntry("preferredDate", "2026-06-30");
        assertThat(merged).containsEntry("preferredTimeWindow", "evening");
    }

    @Test
    void emptyIncomingValuesDoNotRemoveExistingState() {
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("doctorName", "Dr Ashish Shri");
        current.put("preferredDate", "2026-06-30");

        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("preferredDate", "");
        incoming.put("slots", List.of());

        Map<String, Object> merged = service.merge(current, incoming);

        assertThat(merged).containsEntry("doctorName", "Dr Ashish Shri");
        assertThat(merged).containsEntry("preferredDate", "2026-06-30");
        assertThat(merged).doesNotContainKey("slots");
    }
}
