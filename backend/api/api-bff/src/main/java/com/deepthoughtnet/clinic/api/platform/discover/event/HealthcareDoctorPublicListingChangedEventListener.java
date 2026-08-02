package com.deepthoughtnet.clinic.api.platform.discover.event;

import com.deepthoughtnet.clinic.api.platform.discover.HealthcarePublicListingSyncService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HealthcareDoctorPublicListingChangedEventListener implements ModuleBusinessEventListener<HealthcareDoctorPublicListingChangedEvent> {
    private static final Logger log = LoggerFactory.getLogger(HealthcareDoctorPublicListingChangedEventListener.class);

    private final HealthcarePublicListingSyncService syncService;

    public HealthcareDoctorPublicListingChangedEventListener(HealthcarePublicListingSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public String listenerName() {
        return "healthcareDoctorPublicListingChanged";
    }

    @Override
    public String listenerModule() {
        return "DISCOVER";
    }

    @Override
    public String eventType() {
        return "HEALTHCARE_DOCTOR_PUBLIC_LISTING_CHANGED";
    }

    @Override
    public Class<HealthcareDoctorPublicListingChangedEvent> eventClass() {
        return HealthcareDoctorPublicListingChangedEvent.class;
    }

    @Override
    public void handle(HealthcareDoctorPublicListingChangedEvent event) {
        if (event == null) {
            return;
        }
        log.info("healthcare doctor public listing event processed tenantId={} aggregateId={} correlationId={}", event.tenantId(), event.aggregateId(), event.correlationId());
        syncService.syncTenant(event.tenantId(), event.actorId(), event.payload() == null ? "healthcare.doctor.public-listing.changed" : event.payload().reason());
    }
}
