package com.deepthoughtnet.clinic.appointment.events;

import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Diagnostic listener that proves appointment business facts remain owned by
 * the appointment module rather than the generic platform event store.
 */
@Component
public class AppointmentBookedEventListener implements ModuleBusinessEventListener<AppointmentBookedEvent> {
    private static final Logger log = LoggerFactory.getLogger(AppointmentBookedEventListener.class);

    @Override
    public String listenerName() {
        return "appointmentBookedDiagnostics";
    }

    @Override
    public String listenerModule() {
        return "APPOINTMENT";
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
    public void handle(AppointmentBookedEvent event) {
        if (event == null) {
            return;
        }
        log.info(
                "module_event_listener appointment eventId={} eventType={} tenantId={} aggregateId={} appointmentId={} patientId={} doctorUserId={} correlationId={}",
                event.eventId(),
                event.eventType(),
                event.tenantId(),
                event.aggregateId(),
                event.payload() == null ? null : event.payload().appointmentId(),
                event.payload() == null ? null : event.payload().patientId(),
                event.payload() == null ? null : event.payload().doctorUserId(),
                event.correlationId()
        );
    }
}
