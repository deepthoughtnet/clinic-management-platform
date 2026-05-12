package com.deepthoughtnet.clinic.messaging.spi;

/**
 * Logical recipient details for provider-neutral message dispatch.
 */
public record MessageRecipient(
        String address,
        String name
) {
    public MessageRecipient {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address is required");
        }
    }
}
