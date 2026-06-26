package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PatientPortalCareAiWorkflowRegistry {
    private static final Logger log = LoggerFactory.getLogger(PatientPortalCareAiWorkflowRegistry.class);

    private final Map<PatientPortalCareAiWorkflowType, PatientPortalCareAiWorkflowDefinition> definitions;
    private final Map<PatientPortalCareAiIntent, PatientPortalCareAiWorkflowType> intentIndex;

    PatientPortalCareAiWorkflowRegistry() {
        Map<PatientPortalCareAiWorkflowType, PatientPortalCareAiWorkflowDefinition> byWorkflow =
                new EnumMap<>(PatientPortalCareAiWorkflowType.class);
        Map<PatientPortalCareAiIntent, PatientPortalCareAiWorkflowType> byIntent =
                new EnumMap<>(PatientPortalCareAiIntent.class);

        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT, PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                Set.of(PatientPortalCareAiWorkflowEntity.DOCTOR, PatientPortalCareAiWorkflowEntity.DATE, PatientPortalCareAiWorkflowEntity.TIME_SLOT),
                Set.of(PatientPortalCareAiWorkflowEntity.CLINIC, PatientPortalCareAiWorkflowEntity.SPECIALITY, PatientPortalCareAiWorkflowEntity.LOCATION),
                true,
                true,
                Set.of("BOOKED", "CONFIRMED", "SCHEDULED"),
                EnumSet.of(
                        PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                        PatientPortalCareAiWorkflowType.FIND_CLINIC,
                        PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                        PatientPortalCareAiWorkflowType.NONE
                ),
                "Book a new appointment using doctor, date, and slot selection.");
        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT, PatientPortalCareAiIntent.CHECK_APPOINTMENT,
                Set.of(),
                Set.of(PatientPortalCareAiWorkflowEntity.APPOINTMENT),
                false,
                true,
                Set.of("BOOKED", "CONFIRMED", "SCHEDULED"),
                EnumSet.of(
                        PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                        PatientPortalCareAiWorkflowType.FIND_CLINIC,
                        PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                        PatientPortalCareAiWorkflowType.NONE
                ),
                "Review upcoming appointments for the authenticated patient.");
        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT, PatientPortalCareAiIntent.CANCEL_APPOINTMENT,
                Set.of(PatientPortalCareAiWorkflowEntity.APPOINTMENT),
                Set.of(),
                true,
                true,
                Set.of("CANCELLED"),
                EnumSet.of(
                        PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                        PatientPortalCareAiWorkflowType.FIND_CLINIC,
                        PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                        PatientPortalCareAiWorkflowType.NONE
                ),
                "Cancel an existing appointment after explicit confirmation.");
        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT, PatientPortalCareAiIntent.RESCHEDULE_APPOINTMENT,
                Set.of(PatientPortalCareAiWorkflowEntity.APPOINTMENT, PatientPortalCareAiWorkflowEntity.DATE, PatientPortalCareAiWorkflowEntity.TIME_SLOT),
                Set.of(PatientPortalCareAiWorkflowEntity.CLINIC, PatientPortalCareAiWorkflowEntity.DOCTOR),
                true,
                true,
                Set.of("RESCHEDULED"),
                EnumSet.of(
                        PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                        PatientPortalCareAiWorkflowType.FIND_CLINIC,
                        PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                        PatientPortalCareAiWorkflowType.NONE
                ),
                "Move an existing appointment to a new date and slot.");
        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.FIND_DOCTOR, PatientPortalCareAiIntent.FIND_DOCTOR,
                Set.of(PatientPortalCareAiWorkflowEntity.SPECIALITY),
                Set.of(PatientPortalCareAiWorkflowEntity.DOCTOR, PatientPortalCareAiWorkflowEntity.CLINIC, PatientPortalCareAiWorkflowEntity.LOCATION),
                false,
                false,
                Set.of(),
                EnumSet.of(
                        PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                        PatientPortalCareAiWorkflowType.FIND_CLINIC,
                        PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                        PatientPortalCareAiWorkflowType.NONE
                ),
                "Find a doctor by name, speciality, or availability.");
        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.FIND_CLINIC, PatientPortalCareAiIntent.FIND_CLINIC,
                Set.of(),
                Set.of(PatientPortalCareAiWorkflowEntity.CLINIC, PatientPortalCareAiWorkflowEntity.LOCATION),
                false,
                false,
                Set.of(),
                EnumSet.of(
                        PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                        PatientPortalCareAiWorkflowType.FIND_CLINIC,
                        PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                        PatientPortalCareAiWorkflowType.NONE
                ),
                "Find a clinic or location for the patient.");
        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.RESET_CONVERSATION, PatientPortalCareAiIntent.RESET_CONVERSATION,
                Set.of(),
                Set.of(),
                false,
                true,
                Set.of("CANCELLED", "COMPLETED"),
                EnumSet.allOf(PatientPortalCareAiWorkflowType.class),
                "Clear the active patient portal CareAI conversation state.");
        register(byWorkflow, byIntent, PatientPortalCareAiWorkflowType.NONE, PatientPortalCareAiIntent.UNKNOWN,
                Set.of(),
                Set.of(),
                false,
                false,
                Set.of(),
                EnumSet.of(
                        PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                        PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                        PatientPortalCareAiWorkflowType.FIND_CLINIC,
                        PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                        PatientPortalCareAiWorkflowType.NONE
                ),
                "No active workflow.");
        this.definitions = Map.copyOf(byWorkflow);
        this.intentIndex = Map.copyOf(byIntent);
    }

    PatientPortalCareAiWorkflowDefinition definitionFor(PatientPortalCareAiWorkflowType workflowType) {
        PatientPortalCareAiWorkflowDefinition definition = definitions.get(workflowType);
        trace(definition);
        return definition;
    }

    PatientPortalCareAiWorkflowDefinition definitionForIntent(PatientPortalCareAiIntent intent) {
        PatientPortalCareAiIntent normalizedIntent = PatientPortalCareAiIntent.normalize(intent);
        if (normalizedIntent == null) {
            trace(null);
            return null;
        }
        PatientPortalCareAiWorkflowType workflowType = intentIndex.get(normalizedIntent);
        PatientPortalCareAiWorkflowDefinition definition = workflowType == null ? null : definitions.get(workflowType);
        trace(definition);
        return definition;
    }

    PatientPortalCareAiWorkflowType workflowForIntent(PatientPortalCareAiIntent intent) {
        PatientPortalCareAiIntent normalizedIntent = PatientPortalCareAiIntent.normalize(intent);
        return normalizedIntent == null ? null : intentIndex.get(normalizedIntent);
    }

    boolean allowsTransition(PatientPortalCareAiWorkflowType from, PatientPortalCareAiWorkflowType to) {
        if (to == null) {
            return false;
        }
        PatientPortalCareAiWorkflowDefinition definition = definitions.get(to);
        if (definition == null) {
            return false;
        }
        return from == null || definition.allowedTransitions().contains(from);
    }

    private void register(Map<PatientPortalCareAiWorkflowType, PatientPortalCareAiWorkflowDefinition> definitions,
                          Map<PatientPortalCareAiIntent, PatientPortalCareAiWorkflowType> intentIndex,
                          PatientPortalCareAiWorkflowType workflowType,
                          PatientPortalCareAiIntent entryIntent,
                          Set<PatientPortalCareAiWorkflowEntity> requiredEntities,
                          Set<PatientPortalCareAiWorkflowEntity> optionalEntities,
                          boolean confirmationRequired,
                          boolean supportsInterruption,
                          Set<String> terminalStatuses,
                          Set<PatientPortalCareAiWorkflowType> allowedTransitions,
                          String description) {
        definitions.put(workflowType, new PatientPortalCareAiWorkflowDefinition(
                workflowType,
                entryIntent,
                Set.copyOf(requiredEntities),
                Set.copyOf(optionalEntities),
                confirmationRequired,
                supportsInterruption,
                Set.copyOf(terminalStatuses),
                Set.copyOf(allowedTransitions),
                description
        ));
        intentIndex.put(entryIntent, workflowType);
        if (workflowType == PatientPortalCareAiWorkflowType.NONE) {
            intentIndex.put(PatientPortalCareAiIntent.GREETING, workflowType);
            intentIndex.put(PatientPortalCareAiIntent.SMALL_TALK, workflowType);
            intentIndex.put(PatientPortalCareAiIntent.UNKNOWN, workflowType);
        }
    }

    private void trace(PatientPortalCareAiWorkflowDefinition definition) {
        if (definition == null || !log.isInfoEnabled()) {
            return;
        }
        log.info(
                "CAREAI_TRACE_WORKFLOW_REGISTRY workflowType={} requiredEntities={} optionalEntities={} confirmationRequired={} supportsInterruption={} terminalStatuses={} allowedTransitions={}",
                definition.workflowType(),
                definition.requiredEntities(),
                definition.optionalEntities(),
                definition.confirmationRequired(),
                definition.supportsInterruption(),
                definition.terminalStatuses(),
                definition.allowedTransitions()
        );
    }
}
