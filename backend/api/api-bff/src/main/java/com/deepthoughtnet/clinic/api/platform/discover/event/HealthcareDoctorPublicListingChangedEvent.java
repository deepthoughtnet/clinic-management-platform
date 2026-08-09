package com.deepthoughtnet.clinic.api.platform.discover.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

public record HealthcareDoctorPublicListingChangedEvent(
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
    public static HealthcareDoctorPublicListingChangedEvent changed(
            UUID tenantId,
            UUID doctorUserId,
            String publicDoctorReference,
            boolean publicListingEnabled,
            String publicationStatus,
            String reason,
            long sourceRevision,
            UUID actorId
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        String correlationId = currentCorrelationId();
        return new HealthcareDoctorPublicListingChangedEvent(
                deterministicEventId(
                        "HEALTHCARE_DOCTOR_PUBLIC_LISTING_CHANGED",
                        tenantId,
                        doctorUserId,
                        publicationStatus,
                        reason,
                        sourceRevision
                ),
                "HEALTHCARE_DOCTOR_PUBLIC_LISTING_CHANGED",
                1,
                now,
                tenantId,
                "HEALTHCARE",
                "DOCTOR_PROFILE",
                doctorUserId,
                correlationId,
                correlationId,
                actorId,
                new Payload(
                        new PublicProviderReference(publicDoctorReference, null),
                        publicListingEnabled,
                        publicationStatus,
                        reason,
                        sourceRevision
                )
        );
    }

    public record Payload(
            PublicProviderReference publicReference,
            boolean publicListingEnabled,
            String publicationStatus,
            String reason,
            long sourceRevision
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
