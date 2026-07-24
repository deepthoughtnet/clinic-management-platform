package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.appointment.events.AppointmentReminderDueEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class AppointmentReminderDueStaffNotificationListener extends AbstractStaffNotificationListener<AppointmentReminderDueEvent> {
    private final NotificationCenterRequestFactory factory;

    public AppointmentReminderDueStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterAppointmentReminderDue";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
    }

    @Override
    public String eventType() {
        return "APPOINTMENT_REMINDER_DUE";
    }

    @Override
    public Class<AppointmentReminderDueEvent> eventClass() {
        return AppointmentReminderDueEvent.class;
    }

    @Override
    protected StaffNotificationRequest toRequest(AppointmentReminderDueEvent event) {
        return factory.fromAppointmentReminderDue(event);
    }
}
