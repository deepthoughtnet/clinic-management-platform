package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentCancelledEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class AppointmentCancelledStaffNotificationListener extends AbstractStaffNotificationListener<AppointmentCancelledEvent> {
    private final NotificationCenterRequestFactory factory;

    public AppointmentCancelledStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterAppointmentCancelled";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_CANCELLED";
    }

    @Override
    public Class<AppointmentCancelledEvent> eventClass() {
        return AppointmentCancelledEvent.class;
    }

    @Override
    protected StaffNotificationRequest toRequest(AppointmentCancelledEvent event) {
        return factory.fromAppointmentCancelled(event);
    }
}
