package com.deepthoughtnet.clinic.realtime.voice.receptionist;

import java.util.UUID;

/**
 * Result object for each receptionist workflow step.
 */
public record ReceptionistWorkflowResult(
        ReceptionistIntent intent,
        double confidence,
        String promptKey,
        String userTextForLlm,
        String deterministicReply,
        boolean escalate,
        String escalationReason,
        String escalationCategory,
        String escalationPriority,
        String extractionSummary,
        String workflowState,
        UUID appointmentId,
        boolean confirmationPending
) {
}
