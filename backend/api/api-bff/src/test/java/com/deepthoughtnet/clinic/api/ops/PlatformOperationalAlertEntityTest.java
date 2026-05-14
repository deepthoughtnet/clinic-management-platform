package com.deepthoughtnet.clinic.api.ops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.deepthoughtnet.clinic.api.ops.db.PlatformOperationalAlertEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformOperationalAlertEntityTest {
    @Test
    void openCreatesAlertWithOpenStatus() {
        UUID tenantId = UUID.randomUUID();
        PlatformOperationalAlertEntity alert = PlatformOperationalAlertEntity.open(
                tenantId,
                "PROVIDER_FAILURE",
                "PROVIDER_FAILURE",
                PlatformOperationalAlertEntity.Severity.CRITICAL,
                "carepilot-messaging",
                "carepilot-messaging",
                "corr-1",
                "Provider degraded"
        );

        assertNotNull(alert.getId());
        assertEquals(tenantId, alert.getTenantId());
        assertEquals(PlatformOperationalAlertEntity.Status.OPEN, alert.getStatus());
        assertEquals("PROVIDER_FAILURE", alert.getAlertType());
    }
}
