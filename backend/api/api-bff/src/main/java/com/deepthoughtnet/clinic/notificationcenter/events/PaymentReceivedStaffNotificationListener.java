package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.billing.events.PaymentReceivedEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class PaymentReceivedStaffNotificationListener extends AbstractStaffNotificationListener<PaymentReceivedEvent> {
    private final NotificationCenterRequestFactory factory;

    public PaymentReceivedStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterPaymentReceived";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
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
    protected StaffNotificationRequest toRequest(PaymentReceivedEvent event) {
        return factory.fromPaymentReceived(event);
    }
}
