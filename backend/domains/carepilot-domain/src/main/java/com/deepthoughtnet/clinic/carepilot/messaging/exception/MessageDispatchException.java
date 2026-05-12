package com.deepthoughtnet.clinic.carepilot.messaging.exception;

/**
 * Raised when message orchestration cannot safely complete.
 */
public class MessageDispatchException extends RuntimeException {
    public MessageDispatchException(String message) {
        super(message);
    }

    public MessageDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
