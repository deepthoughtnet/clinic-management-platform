package com.deepthoughtnet.clinic.notification.service;

import com.deepthoughtnet.clinic.notification.service.impl.NotificationOutboxDispatcher;
import org.springframework.stereotype.Service;

/**
 * Named service wrapper requested by reliability hardening task.
 * Scheduling remains in NotificationOutboxDispatcher.
 */
@Service
public class OutboxDispatcher {
    private final NotificationOutboxDispatcher delegate;

    public OutboxDispatcher(NotificationOutboxDispatcher delegate) {
        this.delegate = delegate;
    }

    public void dispatchDue() {
        delegate.dispatchDueNotifications();
    }
}
