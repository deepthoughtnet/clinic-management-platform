package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PatientPortalCareAiWorkflowSubStateRegistryTest {
    private final PatientPortalCareAiWorkflowSubStateRegistry registry = new PatientPortalCareAiWorkflowSubStateRegistry();

    @Test
    void bookingWorkflowStartsWithProviderOrSpecialtyCollection() {
        assertThat(registry.initialStateFor(PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT))
                .isEqualTo(PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY);
    }

    @Test
    void rescheduleWorkflowStartsWithAppointmentSelection() {
        assertThat(registry.initialStateFor(PatientPortalCareAiWorkflowType.RESCHEDULE_APPOINTMENT))
                .isEqualTo(PatientPortalCareAiWorkflowSubState.NEED_APPOINTMENT);
    }

    @Test
    void readOnlyWorkflowsStartResolving() {
        assertThat(registry.initialStateFor(PatientPortalCareAiWorkflowType.FIND_DOCTOR))
                .isEqualTo(PatientPortalCareAiWorkflowSubState.RESOLVING);
        assertThat(registry.initialStateFor(PatientPortalCareAiWorkflowType.FIND_CLINIC))
                .isEqualTo(PatientPortalCareAiWorkflowSubState.RESOLVING);
        assertThat(registry.initialStateFor(PatientPortalCareAiWorkflowType.CHECK_APPOINTMENT))
                .isEqualTo(PatientPortalCareAiWorkflowSubState.RESOLVING);
    }

    @Test
    void bookingWorkflowAllowsBacktrackingToEarlierSubStates() {
        assertThat(registry.allowsTransition(
                PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.CONFIRMATION_PENDING,
                PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION
        )).isTrue();
        assertThat(registry.allowsTransition(
                PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION,
                PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY
        )).isTrue();
    }

    @Test
    void toolExecutionHasAnExplicitWaitingState() {
        assertThat(registry.allowsTransition(
                PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.CHECKING_AVAILABILITY,
                PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL
        )).isTrue();
        assertThat(registry.allowsTransition(
                PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.WAITING_FOR_TOOL,
                PatientPortalCareAiWorkflowSubState.NEED_SLOT_SELECTION
        )).isTrue();
    }

    @Test
    void bookingWorkflowRejectsIllegalTransitions() {
        assertThat(registry.allowsTransition(
                PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.NEED_PROVIDER_OR_SPECIALTY,
                PatientPortalCareAiWorkflowSubState.COMPLETED
        )).isFalse();
    }

    @Test
    void terminalStateCanResetOnNextWorkflowStart() {
        assertThat(registry.allowsTransition(
                PatientPortalCareAiWorkflowType.BOOK_APPOINTMENT,
                PatientPortalCareAiWorkflowSubState.COMPLETED,
                PatientPortalCareAiWorkflowSubState.START
        )).isTrue();
    }
}
