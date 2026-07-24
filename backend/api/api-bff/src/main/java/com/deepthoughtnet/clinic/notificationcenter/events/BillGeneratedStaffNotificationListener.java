package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.billing.events.BillGeneratedEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class BillGeneratedStaffNotificationListener extends AbstractStaffNotificationListener<BillGeneratedEvent> {
    private final NotificationCenterRequestFactory factory;

    public BillGeneratedStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterBillGenerated";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
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
    protected StaffNotificationRequest toRequest(BillGeneratedEvent event) {
        return factory.fromBillGenerated(event);
    }
}
