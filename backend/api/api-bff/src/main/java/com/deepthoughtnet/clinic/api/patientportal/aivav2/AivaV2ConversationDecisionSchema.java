package com.deepthoughtnet.clinic.api.patientportal.aivav2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AivaV2ConversationDecisionSchema {
    private AivaV2ConversationDecisionSchema() {
    }

    static Map<String, Object> definition() {
        Map<String, Object> patch = object(Map.of(
                "doctorText", valuePatch(),
                "specialtyText", valuePatch(),
                "dateExpression", valuePatch(),
                "timeWindow", valuePatch(),
                "exactTime", valuePatch(),
                "clinicText", valuePatch(),
                "availabilityTimeConstraint", constraintPatch()));
        Map<String, Object> selection = object(Map.of(
                "candidateRef", nullableString(),
                "ordinal", Map.of("type", "integer"),
                "slotRef", nullableString(),
                "exactTime", nullableString()));
        Map<String, Object> lookup = object(Map.of(
                "doctorText", valuePatch(),
                "dateExpression", valuePatch(),
                "status", valuePatch(),
                "clinicText", valuePatch(),
                "nextOnly", Map.of("type", "boolean")));
        return object(Map.of(
                "schemaVersion", Map.of("type", "string", "enum", List.of("1.0")),
                "dialogAct", enumString("START_REQUEST", "PROVIDE_INFORMATION", "CHANGE_INFORMATION", "SELECT_OPTION",
                        "REQUEST_ALTERNATIVE", "CONFIRM", "REJECT", "ASK_QUESTION", "ABANDON", "UNKNOWN"),
                "operation", enumString("START_BOOKING", "UPDATE_BOOKING", "RESOLVE_PROVIDER", "GET_AVAILABILITY",
                        "SELECT_SLOT", "SHOW_MORE_SLOTS", "PREPARE_BOOKING", "CONFIRM_BOOKING", "ANSWER_CONTEXT",
                        "SUSPEND_BOOKING", "RESUME_BOOKING", "ABANDON_BOOKING", "LOOKUP_APPOINTMENTS",
                        "CANCEL_APPOINTMENT", "PREPARE_CANCELLATION", "CONFIRM_CANCELLATION",
                        "RESCHEDULE_APPOINTMENT", "UPDATE_RESCHEDULE", "GET_RESCHEDULE_AVAILABILITY",
                        "SELECT_RESCHEDULE_SLOT", "PREPARE_RESCHEDULE", "CONFIRM_RESCHEDULE", "UNKNOWN"),
                "bookingPatch", patch,
                "selection", selection,
                "lookupFilter", lookup,
                "confirmation", enumString("POSITIVE", "NEGATIVE", "AMBIGUOUS", "NONE"),
                "topicAction", enumString("CONTINUE", "SUSPEND", "RESUME", "ABANDON"),
                "responseLanguage", Map.of("type", "string"),
                "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)));
    }

    private static Map<String, Object> valuePatch() {
        return object(Map.of("mode", enumString("UNCHANGED", "SET", "CLEAR"), "value", nullableString()));
    }

    private static Map<String, Object> constraintPatch() {
        return object(Map.of(
                "mode", enumString("UNCHANGED", "SET", "CLEAR"),
                "value", object(Map.of(
                        "mode", enumString("EXACT", "AFTER", "BEFORE", "BETWEEN"),
                        "startTime", nullableString(),
                        "endTime", nullableString()))));
    }

    private static Map<String, Object> enumString(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> nullableString() {
        // Optional properties are omitted for UNCHANGED/empty selection fields.
        return Map.of("type", "string");
    }

    private static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        return schema;
    }
}
