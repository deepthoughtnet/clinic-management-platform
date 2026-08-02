package com.deepthoughtnet.clinic.api.platform.discover.event;

import com.deepthoughtnet.clinic.api.platform.discover.HealthcarePublicListingSyncService;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HealthcareClinicPublicListingChangedEventListener implements ModuleBusinessEventListener<HealthcareClinicPublicListingChangedEvent> {
    private static final Logger log = LoggerFactory.getLogger(HealthcareClinicPublicListingChangedEventListener.class);

    private final HealthcarePublicListingSyncService syncService;

    public HealthcareClinicPublicListingChangedEventListener(HealthcarePublicListingSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public String listenerName() {
        return "healthcareClinicPublicListingChanged";
    }

    @Override
    public String listenerModule() {
        return "DISCOVER";
    }

    @Override
    public String eventType() {
        return "HEALTHCARE_CLINIC_PUBLIC_LISTING_CHANGED";
    }

    @Override
    public Class<HealthcareClinicPublicListingChangedEvent> eventClass() {
        return HealthcareClinicPublicListingChangedEvent.class;
    }

    @Override
    public void handle(HealthcareClinicPublicListingChangedEvent event) {
        if (event == null) {
            return;
        }
        log.info("healthcare clinic public listing event processed tenantId={} aggregateId={} correlationId={}", event.tenantId(), event.aggregateId(), event.correlationId());
        syncService.syncTenant(event.tenantId(), event.actorId(), event.payload() == null ? "healthcare.clinic.public-listing.changed" : event.payload().reason());
    }
}
