package com.deepthoughtnet.clinic.ai.careai.persistence.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

final class CareAiJsonSupport {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private CareAiJsonSupport() {
    }

    static Map<String, Object> parseObject(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid CareAI JSON payload", ex);
        }
    }

    static String writeObject(Map<String, Object> value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value == null ? new LinkedHashMap<>() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize CareAI JSON payload", ex);
        }
    }
}
