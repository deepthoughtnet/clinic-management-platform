package com.deepthoughtnet.clinic.api.patientportal.careai;

@FunctionalInterface
interface PatientPortalCareAiSkillExecutor<I, O> {
    O execute(I input);
}
