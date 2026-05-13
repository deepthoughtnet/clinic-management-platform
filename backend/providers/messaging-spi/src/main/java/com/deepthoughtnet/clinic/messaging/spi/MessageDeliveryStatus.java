package com.deepthoughtnet.clinic.messaging.spi;

/**
 * Delivery outcome classification returned by message providers.
 */
public enum MessageDeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    READ,
    FAILED,
    BOUNCED,
    UNDELIVERED,
    SKIPPED,
    PROVIDER_NOT_AVAILABLE,
    NOT_CONFIGURED,
    UNKNOWN
}
