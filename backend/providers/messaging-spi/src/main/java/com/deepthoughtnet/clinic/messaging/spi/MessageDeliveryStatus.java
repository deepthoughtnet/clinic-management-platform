package com.deepthoughtnet.clinic.messaging.spi;

/**
 * Delivery outcome classification returned by message providers.
 */
public enum MessageDeliveryStatus {
    SENT,
    FAILED,
    SKIPPED,
    PROVIDER_NOT_AVAILABLE,
    NOT_CONFIGURED
}
