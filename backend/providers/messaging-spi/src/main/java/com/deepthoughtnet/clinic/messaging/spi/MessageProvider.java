package com.deepthoughtnet.clinic.messaging.spi;

/**
 * Strategy contract for channel-specific provider implementations.
 */
public interface MessageProvider {
    /**
     * Returns true when this provider can send the supplied channel.
     */
    boolean supports(MessageChannel channel);

    /**
     * Sends a provider-neutral request and returns structured delivery result.
     */
    MessageResult send(MessageRequest request);

    /**
     * Logical provider name used for result/audit visibility.
     */
    String providerName();
}
