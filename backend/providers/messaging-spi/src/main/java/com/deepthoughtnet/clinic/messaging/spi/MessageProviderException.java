package com.deepthoughtnet.clinic.messaging.spi;

/**
 * Provider-level exception that indicates dispatch processing failed unexpectedly.
 */
public class MessageProviderException extends RuntimeException {
    public MessageProviderException(String message) {
        super(message);
    }

    public MessageProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
