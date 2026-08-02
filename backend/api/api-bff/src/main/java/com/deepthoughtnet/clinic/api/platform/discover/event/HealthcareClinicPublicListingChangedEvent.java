package com.deepthoughtnet.clinic.api.platform.discover.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

public record HealthcareClinicPublicListingChangedEvent(
        UUID eventId,
        String eventType,
        int eventVersion,
        OffsetDateTime occurredAt,
        UUID tenantId,
        String sourceModule,
        String aggregateType,
        UUID aggregateId,
        String correlationId,
        String causationId,
        UUID actorId,
        Payload payload
) implements ModuleBusinessEvent {
    public static HealthcareClinicPublicListingChangedEvent changed(
            UUID tenantId,
            UUID clinicId,
            String publicClinicReference,
            boolean publicListingEnabled,
            String publicationStatus,
            String reason,
            UUID actorId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new HealthcareClinicPublicListingChangedEvent(
                deterministicEventId("HEALTHCARE_CLINIC_PUBLIC_LISTING_CHANGED", tenantId, clinicId, publicationStatus, reason),
                "HEALTHCARE_CLINIC_PUBLIC_LISTING_CHANGED",
                1,
                now,
                tenantId,
                "HEALTHCARE",
                "CLINIC_PROFILE",
                clinicId,
                correlationId,
                correlationId,
                actorId,
                new Payload(
                        new PublicProviderReference(publicClinicReference, null),
                        publicListingEnabled,
                        publicationStatus,
                        reason
                )
        );
    }

    public record Payload(
            PublicProviderReference publicReference,
            boolean publicListingEnabled,
            String publicationStatus,
            String reason
    ) implements ModuleBusinessEventPayload {
    }

    private static UUID deterministicEventId(Object... parts) {
        StringBuilder seed = new StringBuilder();
        for (Object part : parts) {
            if (seed.length() > 0) {
                seed.append('|');
            }
            seed.append(part == null ? "" : part.toString());
        }
        return UUID.nameUUIDFromBytes(seed.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String currentCorrelationId() {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("X-Correlation-ID");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId.trim();
    }
}
