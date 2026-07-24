package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.api.lab.events.LabReportPublishedEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class LabReportPublishedStaffNotificationListener extends AbstractStaffNotificationListener<LabReportPublishedEvent> {
    private final NotificationCenterRequestFactory factory;

    public LabReportPublishedStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterLabReportPublished";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
    }

    @Override
    public String eventType() {
        return "LAB_REPORT_PUBLISHED";
    }

    @Override
    public Class<LabReportPublishedEvent> eventClass() {
        return LabReportPublishedEvent.class;
    }

    @Override
    protected StaffNotificationRequest toRequest(LabReportPublishedEvent event) {
        return factory.fromLabReportPublished(event);
    }
}
