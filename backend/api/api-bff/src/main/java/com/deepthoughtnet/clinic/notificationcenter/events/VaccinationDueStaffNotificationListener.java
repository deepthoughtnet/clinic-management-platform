package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import com.deepthoughtnet.clinic.vaccination.events.VaccinationDueEvent;
import org.springframework.stereotype.Component;

@Component
public class VaccinationDueStaffNotificationListener extends AbstractStaffNotificationListener<VaccinationDueEvent> {
    private final NotificationCenterRequestFactory factory;

    public VaccinationDueStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterVaccinationDue";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
    }

    @Override
    public String eventType() {
        return "VACCINATION_DUE";
    }

    @Override
    public Class<VaccinationDueEvent> eventClass() {
        return VaccinationDueEvent.class;
    }

    @Override
    protected StaffNotificationRequest toRequest(VaccinationDueEvent event) {
        return factory.fromVaccinationDue(event);
    }
}
