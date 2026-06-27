package com.deepthoughtnet.clinic.api.patientportal.careai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowInterruptManagerTest {
    private final WorkflowInterruptManager manager = new WorkflowInterruptManager();

    @Test
    void cancelInterruptsActiveBookingWorkflow() {
        WorkflowInterruptManager.WorkflowInterruptDecision decision = manager.evaluate(
                PatientPortalCareAiIntent.BOOK_APPOINTMENT,
                PatientPortalCareAiIntent.CANCEL_APPOINTMENT,
                "cancel my appointment"
        );

        assertThat(decision.interruptOccurred()).isTrue();
        assertThat(decision.resetRequested()).isFalse();
        assertThat(decision.targetIntent()).isEqualTo(PatientPortalCareAiIntent.CANCEL_APPOINTMENT);
    }

    @Test
    void resetRequestsAreDetectedAcrossLanguages() {
        assertThat(manager.isResetRequest("Start again")).isTrue();
        assertThat(manager.isResetRequest("फिर से शुरू करें")).isTrue();
    }
}
