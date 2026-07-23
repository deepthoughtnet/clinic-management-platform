package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.billing.events.PaymentReminderEvent;
import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable listener that converts a payment-reminder fact into notification delivery rows.
 */
@Component
public class PaymentReminderNotificationListener implements ModuleBusinessEventListener<PaymentReminderEvent> {
    private static final Logger log = LoggerFactory.getLogger(PaymentReminderNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public PaymentReminderNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "paymentReminderNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
    }

    @Override
    public String eventType() {
        return "PAYMENT_REMINDER";
    }

    @Override
    public Class<PaymentReminderEvent> eventClass() {
        return PaymentReminderEvent.class;
    }

    @Override
    public void handle(PaymentReminderEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "payment_reminder_notification_listener eventId={} tenantId={} billId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().billId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
