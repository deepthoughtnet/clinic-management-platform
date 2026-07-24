package com.deepthoughtnet.clinic.notificationcenter.events;

import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationPublisher;
import com.deepthoughtnet.clinic.platform.contracts.notificationcenter.StaffNotificationRequest;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractStaffNotificationListener<E extends ModuleBusinessEvent> implements ModuleBusinessEventListener<E> {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    private final StaffNotificationPublisher publisher;

    protected AbstractStaffNotificationListener(StaffNotificationPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void handle(E event) {
        if (event == null) {
            return;
        }
        StaffNotificationRequest request = toRequest(event);
        if (request == null) {
            return;
        }
        try {
            var notificationId = publisher.publish(request);
            log.info(
                    "staff_notification_projected listener={} eventType={} eventId={} tenantId={} notificationId={} sourceEventType={} correlationId={}",
                    listenerName(),
                    event.eventType(),
                    event.eventId(),
                    event.tenantId(),
                    notificationId,
                    request.sourceEventType(),
                    event.correlationId()
            );
        } catch (RuntimeException ex) {
            log.error(
                    "staff_notification_projection_failed listener={} eventType={} eventId={} tenantId={} correlationId={}",
                    listenerName(),
                    event.eventType(),
                    event.eventId(),
                    event.tenantId(),
                    event.correlationId(),
                    ex
            );
            throw ex;
        }
    }

    protected abstract StaffNotificationRequest toRequest(E event);
}
