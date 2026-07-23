package com.deepthoughtnet.clinic.notification.events;

import com.deepthoughtnet.clinic.notification.service.AppointmentBookedNotificationService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import com.deepthoughtnet.clinic.platform.modulith.events.model.LabReportPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Durable listener that turns a patient-visible lab publication fact into notification delivery rows.
 */
@Component
public class LabReportReadyNotificationListener implements ModuleBusinessEventListener<LabReportPublishedEvent> {
    private static final Logger log = LoggerFactory.getLogger(LabReportReadyNotificationListener.class);

    private final AppointmentBookedNotificationService notificationService;

    public LabReportReadyNotificationListener(AppointmentBookedNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public String listenerName() {
        return "labReportReadyNotification";
    }

    @Override
    public String listenerModule() {
        return "NOTIFICATION";
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
    public void handle(LabReportPublishedEvent event) {
        if (event == null) {
            return;
        }
        var result = notificationService.queue(event);
        log.info(
                "lab_report_ready_notification_listener eventId={} tenantId={} labOrderId={} patientId={} created={} correlationId={}",
                event.eventId(),
                event.tenantId(),
                event.payload() == null ? null : event.payload().labOrderId(),
                event.payload() == null ? null : event.payload().patientId(),
                result != null && result.created(),
                event.correlationId()
        );
    }
}
