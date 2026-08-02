package com.deepthoughtnet.clinic.platform.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.LinkLifecycleStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.ProviderSourceReference;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.event.PlatformProviderLinkChangedV1;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEvent;
import com.deepthoughtnet.clinic.platform.modulith.events.ModuleBusinessEventPayload;
import com.deepthoughtnet.clinic.platform.providerintegration.db.AbstractProviderLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkEntity;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicDoctorPracticePlatformLinkEntity;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.MDC;

public record PlatformProviderLinkChangedEvent(
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
        PlatformProviderLinkChangedEventPayload payload
) implements ModuleBusinessEvent {

    public static final String EVENT_TYPE = "PLATFORM_PROVIDER_LINK_CHANGED_V1";

    public static PlatformProviderLinkChangedEvent changed(AbstractProviderLinkEntity entity, String action, String actorReference, OffsetDateTime occurredAt) {
        OffsetDateTime resolvedAt = occurredAt == null ? OffsetDateTime.now() : occurredAt;
        PlatformProviderLinkChangedV1 contractPayload = new PlatformProviderLinkChangedV1(
                entity.getProviderType(),
                new ProviderSourceReference(
                        entity.getSourceSystem(),
                        entity.getSourceEntityReference(),
                        entity.getSourceRevision(),
                        entity.getSourceUpdatedAt()
                ),
                toPublicReference(entity),
                entity.getTenantReference(),
                entity.getPlatformClinicReference(),
                entity.getLinkStatus(),
                entity.getConnectionStatus(),
                entity.getBookingCapability(),
                action
        );
        UUID entityId = entity.getId() == null ? UUID.randomUUID() : entity.getId();
        return new PlatformProviderLinkChangedEvent(
                deterministicEventId(entity, action, resolvedAt),
                EVENT_TYPE,
                1,
                resolvedAt,
                deterministicTenantId(entity),
                "PLATFORM",
                "PUBLIC_PROVIDER_LINK",
                entityId,
                currentCorrelationId(),
                action == null || action.isBlank() ? EVENT_TYPE : action.trim(),
                actorId(entity, actorReference),
                new PlatformProviderLinkChangedEventPayload(contractPayload)
        );
    }

    private static PublicProviderReference toPublicReference(AbstractProviderLinkEntity entity) {
        if (entity instanceof PublicClinicPlatformLinkEntity clinic) {
            return new PublicProviderReference(clinic.getPublicClinicReference(), null);
        }
        PublicDoctorPracticePlatformLinkEntity doctor = (PublicDoctorPracticePlatformLinkEntity) entity;
        return new PublicProviderReference(doctor.getPublicDoctorReference(), doctor.getPublicPracticeReference());
    }

    private static UUID deterministicEventId(AbstractProviderLinkEntity entity, String action, OffsetDateTime occurredAt) {
        String seed = String.join("|",
                EVENT_TYPE,
                entity == null || entity.getProviderType() == null ? "" : entity.getProviderType().name(),
                entity == null || entity.getSourceSystem() == null ? "" : entity.getSourceSystem().name(),
                entity == null ? "" : String.valueOf(entity.getId()),
                entity == null ? "" : String.valueOf(entity.getTenantReference()),
                entity == null ? "" : String.valueOf(entity.getSourceEntityReference()),
                action == null ? "" : action.trim(),
                occurredAt == null ? "" : occurredAt.toString()
        );
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID deterministicTenantId(AbstractProviderLinkEntity entity) {
        String tenantReference = entity == null ? null : entity.getTenantReference();
        String seed = String.join("|",
                "provider-link-tenant-v1",
                entity == null || entity.getProviderType() == null ? "" : entity.getProviderType().name(),
                entity == null || entity.getSourceSystem() == null ? "" : entity.getSourceSystem().name(),
                tenantReference == null ? "" : tenantReference
        );
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID actorId(AbstractProviderLinkEntity entity, String actorReference) {
        String seed = String.join("|",
                "provider-link-actor-v1",
                entity == null || entity.getProviderType() == null ? "" : entity.getProviderType().name(),
                entity == null || entity.getSourceSystem() == null ? "" : entity.getSourceSystem().name(),
                entity == null ? "" : String.valueOf(entity.getTenantReference()),
                actorReference == null ? "" : actorReference.trim()
        );
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
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

    public record PlatformProviderLinkChangedEventPayload(
            PlatformProviderLinkChangedV1 linkChanged
    ) implements ModuleBusinessEventPayload {
    }
}
