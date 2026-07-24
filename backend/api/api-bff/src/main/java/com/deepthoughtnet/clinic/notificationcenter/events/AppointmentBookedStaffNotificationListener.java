package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentBookedEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class AppointmentBookedStaffNotificationListener extends AbstractStaffNotificationListener<AppointmentBookedEvent> {
    private final NotificationCenterRequestFactory factory;

    public AppointmentBookedStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterAppointmentBooked";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_BOOKED";
    }

    @Override
    public Class<AppointmentBookedEvent> eventClass() {
        return AppointmentBookedEvent.class;
    }

    @Override
    protected StaffNotificationRequest toRequest(AppointmentBookedEvent event) {
        return factory.fromAppointmentBooked(event);
    }
}
