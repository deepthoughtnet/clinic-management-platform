package com.deepthoughtnet.clinic.discover.providerownership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class ProviderOwnershipJsonSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ProviderOwnershipJsonSupport() {
    }

    public static JsonNode parseEvidenceSnapshot(String evidenceSnapshotJson) {
        if (evidenceSnapshotJson == null || evidenceSnapshotJson.isBlank()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(evidenceSnapshotJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON evidence snapshot.", ex);
        }
    }

    public static String writeJson(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }
}
