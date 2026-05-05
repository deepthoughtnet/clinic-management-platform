package com.deepthoughtnet.clinic.notify;

import java.util.List;
import java.util.UUID;

public record NotificationMessage(
        UUID tenantId,
        String channel,
        String recipient,
        String subject,
        String body,
        String metadataJson,
        String cc,
        List<NotificationAttachment> attachments
) {
    public NotificationMessage(
            UUID tenantId,
            String channel,
            String recipient,
            String subject,
            String body,
            String metadataJson
    ) {
        this(tenantId, channel, recipient, subject, body, metadataJson, null, List.of());
    }

    public NotificationMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
