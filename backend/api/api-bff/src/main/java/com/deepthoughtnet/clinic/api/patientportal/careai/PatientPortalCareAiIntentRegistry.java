package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class PatientPortalCareAiIntentRegistry {
    private final Map<PatientPortalCareAiIntent, PatientPortalCareAiIntentDefinition> definitions;

    PatientPortalCareAiIntentRegistry() {
        Map<PatientPortalCareAiIntent, PatientPortalCareAiIntentDefinition> byIntent =
                new EnumMap<>(PatientPortalCareAiIntent.class);
        register(byIntent, PatientPortalCareAiIntent.BOOK_APPOINTMENT, PatientPortalCareAiIntent.BOOK_APPOINTMENT, true, true, true, true, 100,
                "book appointment", "book with Dr Vikas", "schedule consultation");
        register(byIntent, PatientPortalCareAiIntent.CHECK_APPOINTMENT, PatientPortalCareAiIntent.CHECK_APPOINTMENT, true, true, true, false, 95,
                "check my appointment", "when is my next appointment", "show appointments");
        register(byIntent, PatientPortalCareAiIntent.CANCEL_APPOINTMENT, PatientPortalCareAiIntent.CANCEL_APPOINTMENT, true, true, true, true, 100,
                "cancel appointment", "cancel my booking", "remove appointment");
        register(byIntent, PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT, PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT, true, true, true, true, 100,
                "reschedule appointment", "change my appointment", "move my appointment");
        register(byIntent, PatientPortalCareAiIntent.FIND_DOCTOR, null, false, false, false, false, 40,
                "find doctor", "show doctor", "which doctor is available");
        register(byIntent, PatientPortalCareAiIntent.FIND_CLINIC, null, false, false, false, false, 40,
                "find clinic", "show clinic", "which clinic is available");
        register(byIntent, PatientPortalCareAiIntent.RESET_CONVERSATION, null, false, true, true, false, 120,
                "start over", "reset conversation", "clear this chat");
        register(byIntent, PatientPortalCareAiIntent.GREETING, null, false, false, false, false, 10,
                "hello", "hi", "good morning");
        register(byIntent, PatientPortalCareAiIntent.SMALL_TALK, null, false, false, false, false, 5,
                "thanks", "thank you", "how are you");
        register(byIntent, PatientPortalCareAiIntent.UNKNOWN, null, false, false, false, false, 0,
                "maybe", "not sure", "help");
        register(byIntent, PatientPortalCareAiIntent.APPOINTMENT_STATUS, PatientPortalCareAiIntent.CHECK_APPOINTMENT, true, true, true, false, 95,
                "appointment status", "next appointment");
        this.definitions = Map.copyOf(byIntent);
    }

    PatientPortalCareAiIntentDefinition definitionFor(PatientPortalCareAiIntent intent) {
        return definitions.get(PatientPortalCareAiIntent.normalize(intent));
    }

    PatientPortalCareAiIntent workflowIntentFor(PatientPortalCareAiIntent intent) {
        PatientPortalCareAiIntentDefinition definition = definitionFor(intent);
        return definition == null ? null : PatientPortalCareAiIntent.normalize(definition.mappedWorkflow());
    }

    private void register(Map<PatientPortalCareAiIntent, PatientPortalCareAiIntentDefinition> definitions,
                          PatientPortalCareAiIntent intentType,
                          PatientPortalCareAiIntent mappedWorkflow,
                          boolean startsWorkflow,
                          boolean interruptsCurrentWorkflow,
                          boolean clearsWorkflowState,
                          boolean requiresConfirmation,
                          int priority,
                          String... exampleUtterances) {
        definitions.put(intentType, new PatientPortalCareAiIntentDefinition(
                intentType,
                PatientPortalCareAiIntent.normalize(mappedWorkflow),
                startsWorkflow,
                interruptsCurrentWorkflow,
                clearsWorkflowState,
                requiresConfirmation,
                priority,
                List.of(exampleUtterances)
        ));
    }
}
