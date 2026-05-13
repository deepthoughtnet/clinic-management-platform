package com.deepthoughtnet.clinic.messaging.whatsapp.meta;

import com.deepthoughtnet.clinic.messaging.spi.MessageRequest;
import com.deepthoughtnet.clinic.messaging.whatsapp.CarePilotWhatsAppMessagingProperties;

/**
 * Transport abstraction for Meta WhatsApp Cloud API.
 */
public interface MetaWhatsAppHttpClient {

    /**
     * Sends a single text message through the configured Meta endpoint.
     */
    MetaWhatsAppHttpResponse sendText(MessageRequest request, CarePilotWhatsAppMessagingProperties properties) throws Exception;
}

