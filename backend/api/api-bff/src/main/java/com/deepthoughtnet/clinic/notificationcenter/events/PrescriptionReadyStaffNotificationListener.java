package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import com.deepthoughtnet.clinic.prescription.events.PrescriptionReadyEvent;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionReadyStaffNotificationListener extends AbstractStaffNotificationListener<PrescriptionReadyEvent> {
    private final NotificationCenterRequestFactory factory;

    public PrescriptionReadyStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterPrescriptionReady";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
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
    protected StaffNotificationRequest toRequest(PrescriptionReadyEvent event) {
        return factory.fromPrescriptionReady(event);
    }
}
