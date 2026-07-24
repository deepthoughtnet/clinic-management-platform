package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentRescheduledEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class AppointmentRescheduledStaffNotificationListener extends AbstractStaffNotificationListener<AppointmentRescheduledEvent> {
    private final NotificationCenterRequestFactory factory;

    public AppointmentRescheduledStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterAppointmentRescheduled";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_RESCHEDULED";
    }

    @Override
    public Class<AppointmentRescheduledEvent> eventClass() {
        return AppointmentRescheduledEvent.class;
    }

    @Override
    protected StaffNotificationRequest toRequest(AppointmentRescheduledEvent event) {
        return factory.fromAppointmentRescheduled(event);
    }
}
