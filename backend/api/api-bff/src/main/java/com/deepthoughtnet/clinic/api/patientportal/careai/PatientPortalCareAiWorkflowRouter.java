package com.deepthoughtnet.clinic.api.patientportal.careai;

final class PatientPortalCareAiWorkflowRouter {
    private final PatientPortalCareAiIntentRegistry intentRegistry;
    private final PatientPortalCareAiWorkflowRegistry workflowRegistry;

    PatientPortalCareAiWorkflowRouter(PatientPortalCareAiIntentRegistry intentRegistry,
                                      PatientPortalCareAiWorkflowRegistry workflowRegistry) {
        this.intentRegistry = intentRegistry;
        this.workflowRegistry = workflowRegistry;
    }

    PatientPortalCareAiWorkflowRouteDecision route(
            PatientPortalCareAiIntent currentWorkflow,
            Object currentState,
            PatientPortalCareAiIntent classifiedIntent,
            String userText
    ) {
        PatientPortalCareAiIntent normalizedCurrent = PatientPortalCareAiIntent.normalize(currentWorkflow);
        PatientPortalCareAiIntent normalizedIntent = PatientPortalCareAiIntent.normalize(classifiedIntent);
        PatientPortalCareAiIntentDefinition definition = intentRegistry.definitionFor(normalizedIntent);
        if (definition == null) {
            return new PatientPortalCareAiWorkflowRouteDecision(normalizedCurrent, false, false, "no-intent-definition");
        }
        PatientPortalCareAiWorkflowType normalizedCurrentWorkflow = workflowRegistry.workflowForIntent(normalizedCurrent);
        if (normalizedIntent == PatientPortalCareAiIntent.RESET_CONVERSATION) {
            return new PatientPortalCareAiWorkflowRouteDecision(null, normalizedCurrent != null, true, "explicit-reset-conversation");
        }
        if (!definition.startsWorkflow()) {
            return new PatientPortalCareAiWorkflowRouteDecision(normalizedCurrent, false, false, "non-workflow-intent");
        }
        PatientPortalCareAiIntent targetWorkflow = PatientPortalCareAiIntent.normalize(definition.mappedWorkflow());
        if (targetWorkflow == null) {
            return new PatientPortalCareAiWorkflowRouteDecision(normalizedCurrent, false, false, "intent-has-no-workflow");
        }
        PatientPortalCareAiWorkflowType targetWorkflowType = workflowRegistry.workflowForIntent(targetWorkflow);
        if (normalizedCurrent == null) {
            return new PatientPortalCareAiWorkflowRouteDecision(
                    targetWorkflow,
                    true,
                    definition.clearsWorkflowState(),
                    "start-workflow-from-explicit-intent"
            );
        }
        if (normalizedCurrent == targetWorkflow) {
            return new PatientPortalCareAiWorkflowRouteDecision(
                    targetWorkflow,
                    false,
                    false,
                    "stay-on-current-workflow"
            );
        }
        if (!workflowRegistry.allowsTransition(normalizedCurrentWorkflow, targetWorkflowType)) {
            return new PatientPortalCareAiWorkflowRouteDecision(
                    normalizedCurrent,
                    false,
                    false,
                    "transition-not-allowed"
            );
        }
        if (definition.interruptsCurrentWorkflow()) {
            return new PatientPortalCareAiWorkflowRouteDecision(
                    targetWorkflow,
                    true,
                    definition.clearsWorkflowState(),
                    "explicit-intent-interrupt"
            );
        }
        return new PatientPortalCareAiWorkflowRouteDecision(
                normalizedCurrent,
                false,
                false,
                "preserve-current-workflow"
        );
    }
}
