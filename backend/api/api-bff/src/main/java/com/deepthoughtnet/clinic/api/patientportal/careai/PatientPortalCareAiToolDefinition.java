package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Set;

record PatientPortalCareAiToolDefinition<I, O>(
        PatientPortalCareAiToolType toolType,
        String skillId,
        Set<PatientPortalCareAiIntent> intents,
        String description,
        Set<PatientPortalCareAiEntityType> requiredEntities,
        Set<PatientPortalCareAiEntityType> optionalEntities,
        boolean confirmationRequired,
        boolean auditRequired,
        String mappedServiceName,
        PatientPortalCareAiSkillConfirmationPolicy confirmationPolicy,
        PatientPortalCareAiSkillAuthorizationPolicy authorizationPolicy,
        Class<I> inputType,
        Class<O> outputType,
        PatientPortalCareAiSkillExecutor<I, O> executor
) implements AivaSkill<I, O> {
    @Override
    public O execute(I input) {
        return executor.execute(input);
    }
}
