package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.consultation.events.FollowUpDueEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable listener that converts a due follow-up fact into notification delivery rows.
 */
@Component
public class FollowUpDueNotificationListener implements ModuleBusinessEventListener<FollowUpDueEvent> {
    private static final Logger log = LoggerFactory.getLogger(FollowUpDueNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public FollowUpDueNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "followUpDueNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "FOLLOW_UP_DUE";
    }

    @Override
    public Class<FollowUpDueEvent> eventClass() {
        return FollowUpDueEvent.class;
    }

    @Override
    public void handle(FollowUpDueEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "follow_up_due_notification_listener eventId={} tenantId={} consultationId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().consultationId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
