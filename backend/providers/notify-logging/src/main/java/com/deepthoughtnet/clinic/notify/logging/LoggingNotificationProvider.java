package com.deepthoughtnet.clinic.notify.logging;

import com.deepthoughtnet.clinic.notify.NotificationMessage;
import com.deepthoughtnet.clinic.notify.NotificationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationProvider.class);

    @Override
    public void send(NotificationMessage message) {
        log.info(
                "Notification prepared. tenantId={}, channel={}, recipient={}, subject={}",
                message.tenantId(),
                message.channel(),
                message.recipient(),
                message.subject()
        );
        if (!message.attachments().isEmpty()) {
            log.info("Notification includes {} attachment(s).", message.attachments().size());
        }
    }
}
