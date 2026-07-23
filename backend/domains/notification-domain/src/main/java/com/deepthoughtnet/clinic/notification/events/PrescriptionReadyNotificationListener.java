package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import com.deepthoughtnet.clinic.prescription.events.PrescriptionReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable listener that converts a finalized prescription business fact into notification-domain delivery rows.
 */
@Component
public class PrescriptionReadyNotificationListener implements ModuleBusinessEventListener<PrescriptionReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(PrescriptionReadyNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public PrescriptionReadyNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "prescriptionReadyNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "PRESCRIPTION_READY";
    }

    @Override
    public Class<PrescriptionReadyEvent> eventClass() {
        return PrescriptionReadyEvent.class;
    }

    @Override
    public void handle(PrescriptionReadyEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "prescription_ready_notification_listener eventId={} tenantId={} prescriptionId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().prescriptionId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
