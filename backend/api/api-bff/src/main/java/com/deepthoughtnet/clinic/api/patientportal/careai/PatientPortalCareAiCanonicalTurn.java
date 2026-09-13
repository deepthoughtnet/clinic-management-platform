package com.deepthoughtnet.clinic.api.patientportal.careai;

public record PatientPortalCareAiCanonicalTurn(
        PatientPortalCareAiDialogAct dialogAct,
        PatientPortalCareAiIntent intent,
        PatientPortalCareAiCanonicalEntities entities,
        PatientPortalCareAiConfirmationPolarity confirmation,
        PatientPortalCareAiCorrection correction,
        PatientPortalCareAiAlternativeRequest alternative,
        PatientPortalCareAiSelectionReference selection,
        boolean abandonWorkflow,
        boolean endConversation,
        PatientPortalCareAiInterpretationSource source,
        double confidence
) {
    public PatientPortalCareAiCanonicalTurn {
        dialogAct = dialogAct == null ? PatientPortalCareAiDialogAct.UNKNOWN : dialogAct;
        entities = entities == null ? new PatientPortalCareAiCanonicalEntities(null, null, null, null, null, null, null, null, null, null) : entities;
        confirmation = confirmation == null ? PatientPortalCareAiConfirmationPolarity.NONE : confirmation;
        correction = correction == null ? PatientPortalCareAiCorrection.none() : correction;
        alternative = alternative == null ? PatientPortalCareAiAlternativeRequest.none() : alternative;
        selection = selection == null ? PatientPortalCareAiSelectionReference.none() : selection;
        source = source == null ? PatientPortalCareAiInterpretationSource.UNKNOWN : source;
        confidence = Math.max(0.0d, Math.min(1.0d, confidence));
    }
}
