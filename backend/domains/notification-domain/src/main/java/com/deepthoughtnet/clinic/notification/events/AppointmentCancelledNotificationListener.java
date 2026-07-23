package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentCancelledEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppointmentCancelledNotificationListener implements ModuleBusinessEventListener<AppointmentCancelledEvent> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentCancelledNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public AppointmentCancelledNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "appointmentCancelledNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_CANCELLED";
    }

    @Override
    public Class<AppointmentCancelledEvent> eventClass() {
        return AppointmentCancelledEvent.class;
    }

    @Override
    public void handle(AppointmentCancelledEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "appointment_cancelled_notification_listener eventId={} tenantId={} appointmentId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().appointmentId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
