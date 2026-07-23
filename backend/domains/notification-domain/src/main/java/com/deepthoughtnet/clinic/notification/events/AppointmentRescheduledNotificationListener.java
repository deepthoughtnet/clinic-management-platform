package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentRescheduledEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AppointmentRescheduledNotificationListener implements ModuleBusinessEventListener<AppointmentRescheduledEvent> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentRescheduledNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public AppointmentRescheduledNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "appointmentRescheduledNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_RESCHEDULED";
    }

    @Override
    public Class<AppointmentRescheduledEvent> eventClass() {
        return AppointmentRescheduledEvent.class;
    }

    @Override
    public void handle(AppointmentRescheduledEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "appointment_rescheduled_notification_listener eventId={} tenantId={} appointmentId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().appointmentId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
