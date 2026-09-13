package com.deepthoughtnet.clinic.llm.sarvam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the logical AIVA schema to Sarvam's JSON-schema response contract. */
final class SarvamResponseSchemaAdapter {
    private SarvamResponseSchemaAdapter() {
    }

    static Map<String, Object> adapt(Map<String, Object> schema) {
        if (schema == null) {
            return null;
        }
        Map<String, Object> result = copyMap(schema);
        Object propertiesValue = result.get("properties");
        if (propertiesValue instanceof Map<?, ?> properties) {
            Map<String, Object> adaptedProperties = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                if (entry.getKey() != null) {
                    adaptedProperties.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
                }
            }
            Object language = adaptedProperties.get("responseLanguage");
            if (language instanceof Map<?, ?>) {
                Map<String, Object> languageSchema = copyMap(language);
                languageSchema.put("type", "string");
                languageSchema.put("description", "Exactly one BCP-47 language tag, such as en, en-IN, or hi-IN. Never return a list.");
                adaptedProperties.put("responseLanguage", languageSchema);
            }
            result.put("properties", adaptedProperties);
            result.put("required", List.of("schemaVersion", "dialogAct", "operation"));
        }
        return result;
    }

    private static Map<String, Object> copyMap(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
                }
            }
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return copyMap(value);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            for (Object item : iterable) {
                copy.add(copyValue(item));
            }
            return copy;
        }
        return value;
    }
}
