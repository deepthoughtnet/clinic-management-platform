package com.deepthoughtnet.clinic.platform.modulith.events;

public class ModuleBusinessEventProcessingException extends RuntimeException {
    private final boolean retryable;

    private ModuleBusinessEventProcessingException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public static ModuleBusinessEventProcessingException retryable(String message, Throwable cause) {
        return new ModuleBusinessEventProcessingException(message, cause, true);
    }

    public static ModuleBusinessEventProcessingException permanent(String message, Throwable cause) {
        return new ModuleBusinessEventProcessingException(message, cause, false);
    }

    public boolean isRetryable() {
        return retryable;
    }
}
