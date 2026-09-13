package com.deepthoughtnet.clinic.llm.groq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts the logical response schema to Groq strict JSON Schema requirements. */
final class GroqResponseSchemaAdapter {
    private static final Set<String> REQUIRED_ROOT_FIELDS = Set.of(
            "schemaVersion", "dialogAct", "operation", "confidence");

    private GroqResponseSchemaAdapter() {
    }

    static Map<String, Object> adapt(Map<String, Object> schema) {
        return schema == null ? null : adaptObject(schema, true, null);
    }

    private static Map<String, Object> adaptObject(Map<String, Object> source, boolean root, String propertyName) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if ("properties".equals(key) && entry.getValue() instanceof Map<?, ?> properties) {
                result.put(key, adaptProperties(properties, root));
            } else {
                result.put(key, adaptValue(entry.getValue(), root, key));
            }
        }
        Object propertiesValue = source.get("properties");
        if (propertiesValue instanceof Map<?, ?> properties) {
            List<String> required = new ArrayList<>();
            for (Object key : properties.keySet()) {
                required.add(String.valueOf(key));
            }
            result.put("required", required);
        }
        if (isNullableProperty(root, propertyName)) {
            makeNullable(result);
        }
        return result;
    }

    private static Map<String, Object> adaptProperties(Map<?, ?> source, boolean parentRoot) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                properties.put(String.valueOf(entry.getKey()), adaptValue(entry.getValue(), parentRoot,
                        String.valueOf(entry.getKey())));
            }
        }
        return properties;
    }

    private static Object adaptValue(Object value, boolean parentRoot, String propertyName) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    typed.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return adaptObject(typed, parentRoot, propertyName);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> values = new ArrayList<>();
            for (Object item : iterable) {
                values.add(adaptValue(item, false, propertyName));
            }
            return values;
        }
        return value;
    }

    private static boolean isNullableProperty(boolean parentRoot, String propertyName) {
        if (propertyName == null) {
            return false;
        }
        if (!parentRoot) {
            return !"mode".equals(propertyName);
        }
        return !REQUIRED_ROOT_FIELDS.contains(propertyName);
    }

    private static void makeNullable(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type instanceof String typeName && !"null".equals(typeName)) {
            schema.put("type", List.of(typeName, "null"));
        }
        Object enumValue = schema.get("enum");
        if (enumValue instanceof Iterable<?> values) {
            List<Object> nullableEnum = new ArrayList<>();
            for (Object value : values) {
                nullableEnum.add(value);
            }
            if (!nullableEnum.contains(null)) {
                nullableEnum.add(null);
            }
            schema.put("enum", nullableEnum);
        }
    }
}
