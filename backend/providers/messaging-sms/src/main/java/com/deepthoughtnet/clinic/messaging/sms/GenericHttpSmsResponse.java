package com.deepthoughtnet.clinic.messaging.sms;

/**
 * Generic HTTP gateway result for SMS dispatch.
 */
public record GenericHttpSmsResponse(
        int statusCode,
        String body,
        String providerMessageId
) {
}

