package com.deepthoughtnet.clinic.carepilot.execution.model;

/** Execution state machine used by scheduler-safe transitions. */
public enum ExecutionStatus {
    QUEUED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    DEAD_LETTER,
    RETRY_SCHEDULED,
    CANCELLED
}
