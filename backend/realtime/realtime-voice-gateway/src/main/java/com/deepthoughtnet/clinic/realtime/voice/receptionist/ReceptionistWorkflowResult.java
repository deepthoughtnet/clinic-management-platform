package com.deepthoughtnet.clinic.realtime.voice.receptionist;

/**
 * Result object for each receptionist workflow step.
 */
public record ReceptionistWorkflowResult(
        String promptKey,
        String userTextForLlm,
        String deterministicReply,
        boolean escalate,
        String escalationReason
) {
}
