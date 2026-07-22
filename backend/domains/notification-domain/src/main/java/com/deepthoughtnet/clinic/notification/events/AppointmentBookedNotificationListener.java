package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable appointment-booked listener that converts the committed appointment fact into a notification-domain
 * request without reaching into notification repositories directly.
 */
@Component
public class AppointmentBookedNotificationListener implements ModuleBusinessEventListener<AppointmentBookedEvent> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentBookedNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public AppointmentBookedNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "appointmentBookedNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_BOOKED";
    }

    @Override
    public Class<AppointmentBookedEvent> eventClass() {
        return AppointmentBookedEvent.class;
    }

    @Override
    public void handle(AppointmentBookedEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "appointment_booked_notification_listener eventId={} tenantId={} appointmentId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().appointmentId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
