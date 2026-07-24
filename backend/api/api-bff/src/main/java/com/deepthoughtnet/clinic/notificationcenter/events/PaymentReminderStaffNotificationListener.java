package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.billing.events.PaymentReminderEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class PaymentReminderStaffNotificationListener extends AbstractStaffNotificationListener<PaymentReminderEvent> {
    private final NotificationCenterRequestFactory factory;

    public PaymentReminderStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterPaymentReminder";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
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
    protected StaffNotificationRequest toRequest(PaymentReminderEvent event) {
        return factory.fromPaymentReminder(event);
    }
}
