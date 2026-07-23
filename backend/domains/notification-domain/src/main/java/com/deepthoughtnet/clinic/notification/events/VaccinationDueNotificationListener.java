package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import com.deepthoughtnet.clinic.platform.modulith.events.model.VaccinationDueEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable listener that converts a due vaccination fact into notification delivery rows.
 */
@Component
public class VaccinationDueNotificationListener implements ModuleBusinessEventListener<VaccinationDueEvent> {
    private static final Logger log = LoggerFactory.getLogger(VaccinationDueNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public VaccinationDueNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "vaccinationDueNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "VACCINATION_DUE";
    }

    @Override
    public Class<VaccinationDueEvent> eventClass() {
        return VaccinationDueEvent.class;
    }

    @Override
    public void handle(VaccinationDueEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "vaccination_due_notification_listener eventId={} tenantId={} vaccinationScheduleEntryId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().vaccinationScheduleEntryId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
