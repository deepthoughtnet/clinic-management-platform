package com.deepthoughtnet.clinic.carepilot.ai_call.model;

/** Execution status for each AI call attempt lifecycle. */
public enum AiCallExecutionStatus {
    PENDING,
    QUEUED,
    DIALING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    NO_ANSWER,
    BUSY,
    CANCELLED,
    ESCALATED,
    SKIPPED,
    SUPPRESSED
}
