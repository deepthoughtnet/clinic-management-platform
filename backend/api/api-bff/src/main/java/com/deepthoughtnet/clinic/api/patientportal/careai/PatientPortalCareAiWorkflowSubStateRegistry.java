package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class PatientPortalCareAiWorkflowSubStateRegistry {
    private final Map<PatientPortalCareAiWorkflowType, PatientPortalCareAiWorkflowSubStateDefinition> definitions;

    PatientPortalCareAiWorkflowSubStateRegistry() {
        Map<PatientPortalCareAiWorkflowType, PatientPortalCareAiWorkflowSubStateDefinition> byWorkflow =
                new EnumMap<>(PatientPortalCareAiWorkflowType.class);

        register(byWorkflow, PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START,
                        PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY,
                        PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS,
                        PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION,
                        PatientPortalCareAiWorkflowSubState.NEED_DATE,
                        PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY,
                        PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION,
                        PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                        PatientPortalCareAiWorkflowSubState.EXECUTING,
                        PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                        PatientPortalCareAiWorkflowSubState.COMPLETED,
                        PatientPortalCareAiWorkflowSubState.FAILED,
                        PatientPortalCareAiWorkflowSubState.CANCELLED
                ),
                transitions(
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS, PatientPortalCareAiWorkflowSubState.NEED_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY),
                        transition(PatientPortalCareAiWorkflowSubState.FINDING_PROVIDERS, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, PatientPortalCareAiWorkflowSubState.NEED_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_DATE, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_DATE, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_DATE, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_DATE, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_DATE, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, PatientPortalCareAiWorkflowSubState.NEED_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, PatientPortalCareAiWorkflowSubState.NEED_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.EXECUTING),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.NEED_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.NEED_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.CANCELLED),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.FAILED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.CANCELLED, PatientPortalCareAiWorkflowSubState.START)
                ),
                "Book appointment workflow sub-states.");

        register(byWorkflow, PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START,
                        PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT,
                        PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE,
                        PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY,
                        PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION,
                        PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                        PatientPortalCareAiWorkflowSubState.EXECUTING,
                        PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                        PatientPortalCareAiWorkflowSubState.COMPLETED,
                        PatientPortalCareAiWorkflowSubState.FAILED,
                        PatientPortalCareAiWorkflowSubState.CANCELLED
                ),
                transitions(
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.EXECUTING),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.NEED_NEW_DATE),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.RESOLVING),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.CANCELLED),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY),
                        transition(PatientPortalCareAiWorkflowSubState.FAILED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.CANCELLED, PatientPortalCareAiWorkflowSubState.START)
                ),
                "Reschedule appointment workflow sub-states.");

        register(byWorkflow, PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START,
                        PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT,
                        PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                        PatientPortalCareAiWorkflowSubState.EXECUTING,
                        PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                        PatientPortalCareAiWorkflowSubState.COMPLETED,
                        PatientPortalCareAiWorkflowSubState.FAILED,
                        PatientPortalCareAiWorkflowSubState.CANCELLED
                ),
                transitions(
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING),
                        transition(PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING, PatientPortalCareAiWorkflowSubState.EXECUTING),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.CANCELLED),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.FAILED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.CANCELLED, PatientPortalCareAiWorkflowSubState.START)
                ),
                "Cancel appointment workflow sub-states.");

        register(byWorkflow, PatientPortalCareAiWorkflowType.FIND_DOCTOR,
                PatientPortalCareAiWorkflowSubState.RESOLVING,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START,
                        PatientPortalCareAiWorkflowSubState.RESOLVING,
                        PatientPortalCareAiWorkflowSubState.EXECUTING,
                        PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                        PatientPortalCareAiWorkflowSubState.COMPLETED,
                        PatientPortalCareAiWorkflowSubState.FAILED
                ),
                transitions(
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.RESOLVING),
                        transition(PatientPortalCareAiWorkflowSubState.RESOLVING, PatientPortalCareAiWorkflowSubState.EXECUTING),
                        transition(PatientPortalCareAiWorkflowSubState.RESOLVING, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.FAILED, PatientPortalCareAiWorkflowSubState.START)
                ),
                "Find doctor workflow sub-states.");

        register(byWorkflow, PatientPortalCareAiWorkflowType.FIND_CLINIC,
                PatientPortalCareAiWorkflowSubState.RESOLVING,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START,
                        PatientPortalCareAiWorkflowSubState.RESOLVING,
                        PatientPortalCareAiWorkflowSubState.EXECUTING,
                        PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                        PatientPortalCareAiWorkflowSubState.COMPLETED,
                        PatientPortalCareAiWorkflowSubState.FAILED
                ),
                transitions(
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.RESOLVING),
                        transition(PatientPortalCareAiWorkflowSubState.RESOLVING, PatientPortalCareAiWorkflowSubState.EXECUTING),
                        transition(PatientPortalCareAiWorkflowSubState.RESOLVING, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.FAILED, PatientPortalCareAiWorkflowSubState.START)
                ),
                "Find clinic workflow sub-states.");

        register(byWorkflow, PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.RESOLVING,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START,
                        PatientPortalCareAiWorkflowSubState.RESOLVING,
                        PatientPortalCareAiWorkflowSubState.EXECUTING,
                        PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                        PatientPortalCareAiWorkflowSubState.COMPLETED,
                        PatientPortalCareAiWorkflowSubState.FAILED
                ),
                transitions(
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.RESOLVING),
                        transition(PatientPortalCareAiWorkflowSubState.RESOLVING, PatientPortalCareAiWorkflowSubState.EXECUTING),
                        transition(PatientPortalCareAiWorkflowSubState.RESOLVING, PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.EXECUTING, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL, PatientPortalCareAiWorkflowSubState.FAILED),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.FAILED, PatientPortalCareAiWorkflowSubState.START)
                ),
                "Check appointment workflow sub-states.");

        register(byWorkflow, PatientPortalCareAiWorkflowType.RESET_CONVERSATION,
                PatientPortalCareAiWorkflowSubState.START,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START,
                        PatientPortalCareAiWorkflowSubState.CANCELLED,
                        PatientPortalCareAiWorkflowSubState.COMPLETED
                ),
                transitions(
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.CANCELLED),
                        transition(PatientPortalCareAiWorkflowSubState.START, PatientPortalCareAiWorkflowSubState.COMPLETED),
                        transition(PatientPortalCareAiWorkflowSubState.CANCELLED, PatientPortalCareAiWorkflowSubState.START),
                        transition(PatientPortalCareAiWorkflowSubState.COMPLETED, PatientPortalCareAiWorkflowSubState.START)
                ),
                "Reset conversation workflow sub-states.");

        register(byWorkflow, PatientPortalCareAiWorkflowType.NONE,
                PatientPortalCareAiWorkflowSubState.START,
                Set.of(
                        PatientPortalCareAiWorkflowSubState.START
                ),
                Set.of(),
                "No active workflow.");

        this.definitions = Map.copyOf(byWorkflow);
    }

    PatientPortalCareAiWorkflowSubStateDefinition definitionFor(PatientPortalCareAiWorkflowType workflowType) {
        return definitions.get(workflowType);
    }

    PatientPortalCareAiWorkflowSubState initialStateFor(PatientPortalCareAiWorkflowType workflowType) {
        PatientPortalCareAiWorkflowSubStateDefinition definition = definitions.get(workflowType);
        return definition == null ? PatientPortalCareAiWorkflowSubState.START : definition.initialState();
    }

    boolean allowsTransition(PatientPortalCareAiWorkflowType workflowType,
                             PatientPortalCareAiWorkflowSubState from,
                             PatientPortalCareAiWorkflowSubState to) {
        if (to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        PatientPortalCareAiWorkflowSubStateDefinition definition = definitions.get(workflowType);
        if (definition == null || !definition.allowedStates().contains(to)) {
            return false;
        }
        if (from == null) {
            return definition.initialState() == to;
        }
        return definition.allowedTransitions().contains(new PatientPortalCareAiWorkflowSubStateTransition(from, to));
    }

    private void register(Map<PatientPortalCareAiWorkflowType, PatientPortalCareAiWorkflowSubStateDefinition> definitions,
                          PatientPortalCareAiWorkflowType workflowType,
                          PatientPortalCareAiWorkflowSubState initialState,
                          Set<PatientPortalCareAiWorkflowSubState> allowedStates,
                          Set<PatientPortalCareAiWorkflowSubStateTransition> allowedTransitions,
                          String description) {
        definitions.put(workflowType, new PatientPortalCareAiWorkflowSubStateDefinition(
                workflowType,
                initialState,
                Set.copyOf(terminalStatesFor(workflowType, allowedStates)),
                Set.copyOf(allowedStates),
                Set.copyOf(allowedTransitions),
                description
        ));
    }

    private Set<PatientPortalCareAiWorkflowSubState> terminalStatesFor(PatientPortalCareAiWorkflowType workflowType,
                                                                       Set<PatientPortalCareAiWorkflowSubState> allowedStates) {
        Set<PatientPortalCareAiWorkflowSubState> terminalStates = new LinkedHashSet<>();
        for (PatientPortalCareAiWorkflowSubState state : allowedStates) {
            if (state == PatientPortalCareAiWorkflowSubState.COMPLETED
                    || state == PatientPortalCareAiWorkflowSubState.FAILED
                    || state == PatientPortalCareAiWorkflowSubState.CANCELLED) {
                terminalStates.add(state);
            }
        }
        return terminalStates;
    }

    private Set<PatientPortalCareAiWorkflowSubStateTransition> transitions(PatientPortalCareAiWorkflowSubStateTransition... transitions) {
        return Set.of(transitions);
    }

    private PatientPortalCareAiWorkflowSubStateTransition transition(PatientPortalCareAiWorkflowSubState from,
                                                                    PatientPortalCareAiWorkflowSubState to) {
        return new PatientPortalCareAiWorkflowSubStateTransition(from, to);
    }
}
