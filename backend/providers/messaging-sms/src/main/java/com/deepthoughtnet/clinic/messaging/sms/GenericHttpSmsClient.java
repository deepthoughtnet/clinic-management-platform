package com.deepthoughtnet.clinic.messaging.sms;

import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;

/**
 * SPI-local transport for generic HTTP SMS dispatch.
 *
 * <p>This indirection keeps provider behavior testable without binding tests to network sockets.</p>
 */
public interface GenericHttpSmsClient {

    /**
     * Sends one SMS request through an HTTP provider endpoint.
     */
    GenericHttpSmsResponse send(MessageRequest request, CarePilotSmsMessagingProperties properties) throws Exception;
}

