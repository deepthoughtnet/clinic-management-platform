package com.deepthoughtnet.clinic.platform.modulith.events;

public enum ModuleBusinessEventStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    RETRY_SCHEDULED,
    FAILED,
    DEAD_LETTERED
}
