package com.deepthoughtnet.clinic.platform.providerintegration.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProfileType;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.SourceSystem;
import com.deepthoughtnet.clinic.platform.providerintegration.db.PublicClinicPlatformLinkEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformProviderLinkChangedEventTest {

    @Test
    void preservesCanonicalTenantAndActorIdentifiers() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        PublicClinicPlatformLinkEntity link = new PublicClinicPlatformLinkEntity();
        link.setId(UUID.randomUUID());
        link.setProviderType(PublicProfileType.CLINIC);
        link.setSourceSystem(SourceSystem.DISCOVER_PROVIDER);
        link.setTenantReference(tenantId.toString());

        PlatformProviderLinkChangedEvent event = PlatformProviderLinkChangedEvent.changed(
                link,
                "PUBLIC_CLINIC_PLATFORM_LINK_PROPOSED",
                actorId.toString(),
                OffsetDateTime.parse("2026-08-06T17:00:00Z")
        );

        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.actorId()).isEqualTo(actorId);
    }
}
