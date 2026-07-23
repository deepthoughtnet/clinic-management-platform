package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.billing.events.BillGeneratedEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable listener that converts a patient-visible bill issuance into notification delivery rows.
 */
@Component
public class BillGeneratedNotificationListener implements ModuleBusinessEventListener<BillGeneratedEvent> {
    private static final Logger log = LoggerFactory.getLogger(BillGeneratedNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public BillGeneratedNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "billGeneratedNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "BILL_GENERATED";
    }

    @Override
    public Class<BillGeneratedEvent> eventClass() {
        return BillGeneratedEvent.class;
    }

    @Override
    public void handle(BillGeneratedEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "bill_generated_notification_listener eventId={} tenantId={} billId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().billId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
