package com.deepthoughtnet.clinic.communication.db;

import com.deepthoughtnet.clinic.communication.model.CommunicationChannelType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CommunicationChannelEntityTest {
    @Test
    void tenantScopedChannelRetainsNullClinicAndLocation() {
        UUID tenantId = UUID.randomUUID();

        CommunicationChannelEntity channel = CommunicationChannelEntity.create(
                tenantId, null, null, CommunicationChannelType.VOICE, "DOTVOICE", "00919999000001",
                "+919999000001", null, null, null, true, true, true, false, null);

        assertEquals(tenantId, channel.getTenantId());
        assertNull(channel.getClinicId());
        assertNull(channel.getLocationId());
        assertEquals("+919999000001", channel.getNormalizedAddress());
    }
}
