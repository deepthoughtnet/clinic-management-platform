package com.deepthoughtnet.clinic.messaging.whatsapp.meta;

/**
 * Raw HTTP response details from Meta WhatsApp Cloud API.
 */
public record MetaWhatsAppHttpResponse(
        int statusCode,
        String body,
        String messageId
) {
}

