package com.deepthoughtnet.clinic.carepilot.ai_call.model;

/** Event types captured for AI call execution history/timeline. */
public enum AiCallEventType {
    QUEUED,
    DISPATCHED,
    PROVIDER_ACCEPTED,
    RINGING,
    ANSWERED,
    COMPLETED,
    FAILED,
    NO_ANSWER,
    BUSY,
    CANCELLED,
    ESCALATED,
    SKIPPED,
    SUPPRESSED,
    TRANSCRIPT_RECEIVED,
    RETRY_SCHEDULED,
    FAILOVER_ATTEMPTED
}
