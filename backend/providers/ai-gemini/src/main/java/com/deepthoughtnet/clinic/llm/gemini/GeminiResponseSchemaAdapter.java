package com.deepthoughtnet.clinic.llm.gemini;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converts the logical response schema to the subset accepted by Gemini responseSchema. */
final class GeminiResponseSchemaAdapter {
    private static final java.util.Set<String> UNSUPPORTED_KEYS = java.util.Set.of(
            "additionalProperties", "minimum", "maximum");

    private GeminiResponseSchemaAdapter() {
    }

    static Map<String, Object> adapt(Map<String, Object> schema) {
        return schema == null ? null : adaptObject(schema);
    }

    private static Map<String, Object> adaptObject(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (UNSUPPORTED_KEYS.contains(entry.getKey())) {
                continue;
            }
            result.put(entry.getKey(), adaptValue(entry.getValue()));
        }
        return result;
    }

    private static Object adaptValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    typed.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return adaptObject(typed);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> values = new ArrayList<>();
            for (Object item : iterable) {
                values.add(adaptValue(item));
            }
            return values;
        }
        return value;
    }
}
