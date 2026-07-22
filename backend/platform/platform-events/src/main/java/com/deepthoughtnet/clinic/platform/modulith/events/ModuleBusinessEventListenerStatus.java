package com.deepthoughtnet.clinic.platform.modulith.events;

public enum ModuleBusinessEventListenerStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    RETRY_SCHEDULED,
    FAILED,
    DEAD_LETTERED
}
