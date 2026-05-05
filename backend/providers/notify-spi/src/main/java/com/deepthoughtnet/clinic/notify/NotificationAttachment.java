package com.deepthoughtnet.clinic.notify;

public record NotificationAttachment(
        String filename,
        String contentType,
        byte[] content
) {
}
