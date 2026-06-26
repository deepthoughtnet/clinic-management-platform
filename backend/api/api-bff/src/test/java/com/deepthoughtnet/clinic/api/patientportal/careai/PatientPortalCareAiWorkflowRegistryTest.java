package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatientPortalCareAiWorkflowRegistryTest {
    private final PatientPortalCareAiWorkflowRegistry registry = new PatientPortalCareAiWorkflowRegistry();

    @Test
    void bookAppointmentDefinitionIncludesDoctorDateAndSlot() {
        PatientPortalCareAiWorkflowDefinition definition = registry.definitionFor(PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT);
        assertThat(definition.workflowType()).isEqualTo(PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT);
        assertThat(definition.requiredEntities()).containsExactlyInAnyOrder(
                PatientPortalCareAiWorkflowEntity.DOCTOR,
                PatientPortalCareAiWorkflowEntity.DATE,
                PatientPortalCareAiWorkflowEntity.TIME_SLOT
        );
        assertThat(definition.confirmationRequired()).isTrue();
    }

    @Test
    void cancelAppointmentDefinitionRequiresAppointmentAndConfirmation() {
        PatientPortalCareAiWorkflowDefinition definition = registry.definitionFor(PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT);
        assertThat(definition.requiredEntities()).containsExactly(PatientPortalCareAiWorkflowEntity.APPOINTMENT);
        assertThat(definition.confirmationRequired()).isTrue();
    }

    @Test
    void checkAppointmentDefinitionUsesPatientIdentityOnly() {
        PatientPortalCareAiWorkflowDefinition definition = registry.definitionFor(PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT);
        assertThat(definition.requiredEntities()).isEmpty();
        assertThat(definition.optionalEntities()).contains(PatientPortalCareAiWorkflowEntity.APPOINTMENT);
        assertThat(definition.confirmationRequired()).isFalse();
    }

    @Test
    void rescheduleAppointmentDefinitionRequiresAppointmentDateAndSlot() {
        PatientPortalCareAiWorkflowDefinition definition = registry.definitionFor(PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT);
        assertThat(definition.requiredEntities()).containsExactlyInAnyOrder(
                PatientPortalCareAiWorkflowEntity.APPOINTMENT,
                PatientPortalCareAiWorkflowEntity.DATE,
                PatientPortalCareAiWorkflowEntity.TIME_SLOT
        );
        assertThat(definition.confirmationRequired()).isTrue();
    }

    @Test
    void greetingAndUnknownMapToNoneWorkflow() {
        assertThat(registry.workflowForIntent(PatientPortalCareAiIntent.GREETING)).isEqualTo(PatientPortalCareAiWorkflowType.NONE);
        assertThat(registry.workflowForIntent(PatientPortalCareAiIntent.UNKNOWN)).isEqualTo(PatientPortalCareAiWorkflowType.NONE);
    }

    @Test
    void allowedTransitionsAreConfigured() {
        PatientPortalCareAiWorkflowDefinition definition = registry.definitionFor(PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT);
        assertThat(definition.allowedTransitions()).contains(PatientPortalCareAiWorkflowType.CANCEL_APPOINTMENT);
        assertThat(definition.allowedTransitions()).contains(PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT);
        assertThat(definition.allowedTransitions()).contains(PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT);
    }
}
