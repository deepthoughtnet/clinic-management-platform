package com.deepthoughtnet.clinic.api.lab.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Diagnostic listener that keeps the lab publication fact local to the lab
 * workflow while the generic platform module only stores and dispatches it.
 */
@Component
public class LabReportPublishedEventListener implements ModuleBusinessEventListener<LabReportPublishedEvent> {
    private static final Logger log = LoggerFactory.getLogger(LabReportPublishedEventListener.class);

    @Override
    public String listenerName() {
        return "labReportPublishedDiagnostics";
    }

    @Override
    public String listenerModule() {
        return "LAB";
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
        log.info(
                "module_event_listener lab eventId={} eventType={} tenantId={} labOrderId={} patientId={} consultationId={} reportFilename={} deliveryStatus={} correlationId={}",
                event.eventId(),
                event.eventType(),
                event.tenantId(),
                event.aggregateId(),
                event.payload() == null ? null : event.payload().patientId(),
                event.payload() == null ? null : event.payload().consultationId(),
                event.payload() == null ? null : event.payload().reportFilename(),
                event.payload() == null ? null : event.payload().deliveryStatus(),
                event.correlationId()
        );
    }
}
