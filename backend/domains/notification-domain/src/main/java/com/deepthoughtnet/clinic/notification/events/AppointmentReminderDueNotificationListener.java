package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentReminderDueEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppointmentReminderDueNotificationListener implements ModuleBusinessEventListener<AppointmentReminderDueEvent> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderDueNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public AppointmentReminderDueNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "appointmentReminderDueNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_REMINDER_DUE";
    }

    @Override
    public Class<AppointmentReminderDueEvent> eventClass() {
        return AppointmentReminderDueEvent.class;
    }

    @Override
    public void handle(AppointmentReminderDueEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "appointment_reminder_due_notification_listener eventId={} tenantId={} appointmentId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().appointmentId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
