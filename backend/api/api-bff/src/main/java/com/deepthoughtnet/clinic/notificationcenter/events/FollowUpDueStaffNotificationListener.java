package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.consultation.events.FollowUpDueEvent;
import com.deepthoughtnet.clinic.notificationcenter.service.NotificationCenterRequestFactory;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import org.springframework.stereotype.Component;

@Component
public class FollowUpDueStaffNotificationListener extends AbstractStaffNotificationListener<FollowUpDueEvent> {
    private final NotificationCenterRequestFactory factory;

    public FollowUpDueStaffNotificationListener(StaffNotificationPublisher publisher, NotificationCenterRequestFactory factory) {
        super(publisher);
        this.factory = factory;
    }

    @Override
    public String listenerName() {
        return "notificationCenterFollowUpDue";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION_CENTER";
    }

    @Override
    public String eventType() {
        return "FOLLOW_UP_DUE";
    }

    @Override
    public Class<FollowUpDueEvent> eventClass() {
        return FollowUpDueEvent.class;
    }

    @Override
    protected StaffNotificationRequest toRequest(FollowUpDueEvent event) {
        return factory.fromFollowUpDue(event);
    }
}
