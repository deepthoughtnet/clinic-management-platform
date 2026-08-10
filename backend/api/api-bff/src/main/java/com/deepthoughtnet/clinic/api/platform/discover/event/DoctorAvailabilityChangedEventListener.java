package com.deepthoughtnet.clinic.api.platform.discover.event;

import com.deepthoughtnet.clinic.api.platform.discover.HealthcarePublicListingSyncService;
import com.deepthoughtnet.clinic.appointment.events.DoctorAvailabilityChangedEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DoctorAvailabilityChangedEventListener implements ModuleBusinessEventListener<DoctorAvailabilityChangedEvent> {
    private static final Logger log = LoggerFactory.getLogger(DoctorAvailabilityChangedEventListener.class);

    private final HealthcarePublicListingSyncService syncService;

    public DoctorAvailabilityChangedEventListener(HealthcarePublicListingSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public String listenerName() {
        return "doctorAvailabilityChanged";
    }

    @Override
    public String listenerModule() {
        return "DISCOVER";
    }

    @Override
    public String eventType() {
        return "DOCTOR_AVAILABILITY_CHANGED";
    }

    @Override
    public Class<DoctorAvailabilityChangedEvent> eventClass() {
        return DoctorAvailabilityChangedEvent.class;
    }

    @Override
    public void handle(DoctorAvailabilityChangedEvent event) {
        if (event == null) {
            return;
        }
        String reason = event.payload() == null || event.payload().reason() == null || event.payload().reason().isBlank()
                ? "doctor.availability." + (event.payload() == null || event.payload().action() == null ? "changed" : event.payload().action().toLowerCase(Locale.ROOT))
                : event.payload().reason();
        log.info("doctor availability event processed tenantId={} aggregateId={} correlationId={} action={}", event.tenantId(), event.aggregateId(), event.correlationId(), event.payload() == null ? null : event.payload().action());
        syncService.syncTenant(event.tenantId(), event.actorId(), reason);
    }
}
