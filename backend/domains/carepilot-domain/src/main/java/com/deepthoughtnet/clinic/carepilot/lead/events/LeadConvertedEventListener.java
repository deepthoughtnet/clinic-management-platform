package com.deepthoughtnet.clinic.carepilot.lead.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Diagnostic listener that keeps the lead conversion vocabulary within the
 * carepilot module while leaving the platform event store generic.
 */
@Component
public class LeadConvertedEventListener implements ModuleBusinessEventListener<LeadConvertedEvent> {
    private static final Logger log = LoggerFactory.getLogger(LeadConvertedEventListener.class);

    @Override
    public String listenerName() {
        return "leadConvertedDiagnostics";
    }

    @Override
    public String listenerModule() {
        return "CAREPILOT";
    }

    @Override
    public String eventType() {
        return "LEAD_CONVERTED";
    }

    @Override
    public Class<LeadConvertedEvent> eventClass() {
        return LeadConvertedEvent.class;
    }

    @Override
    public void handle(LeadConvertedEvent event) {
        if (event == null) {
            return;
        }
        log.info(
                "module_event_listener lead eventId={} eventType={} tenantId={} leadId={} patientId={} createdNewPatient={} bookedAppointmentId={} correlationId={}",
                event.eventId(),
                event.eventType(),
                event.tenantId(),
                event.aggregateId(),
                event.payload() == null ? null : event.payload().patientId(),
                event.payload() != null && event.payload().createdNewPatient(),
                event.payload() == null ? null : event.payload().bookedAppointmentId(),
                event.correlationId()
        );
    }
}
