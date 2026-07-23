package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.billing.events.PaymentReceivedEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable listener that converts a persisted payment receipt into notification delivery rows.
 */
@Component
public class PaymentReceivedNotificationListener implements ModuleBusinessEventListener<PaymentReceivedEvent> {
    private static final Logger log = LoggerFactory.getLogger(PaymentReceivedNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public PaymentReceivedNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "paymentReceivedNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "PAYMENT_RECEIVED";
    }

    @Override
    public Class<PaymentReceivedEvent> eventClass() {
        return PaymentReceivedEvent.class;
    }

    @Override
    public void handle(PaymentReceivedEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "payment_received_notification_listener eventId={} tenantId={} paymentId={} billId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().paymentId(),
                event.payload() == null ? null : event.payload().billId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
