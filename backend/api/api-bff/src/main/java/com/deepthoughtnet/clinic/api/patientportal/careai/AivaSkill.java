package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.Set;

interface AivaSkill<I, O> {
    String skillId();

    Set<PatientPortalCareAiIntent> intents();

    String description();

    Set<PatientPortalCareAiEntityType> requiredEntities();

    Set<PatientPortalCareAiEntityType> optionalEntities();

    PatientPortalCareAiSkillConfirmationPolicy confirmationPolicy();

    PatientPortalCareAiSkillAuthorizationPolicy authorizationPolicy();

    Class<I> inputType();

    Class<O> outputType();

    O execute(I input);
}
