package com.deepthoughtnet.clinic.api.platform.discover.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.api.platform.discover.HealthcarePublicListingSyncService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealthcareDoctorPublicListingChangedEventListenerTest {

    @Test
    void handleTriggersTenantReconciliation() {
        HealthcarePublicListingSyncService syncService = mock(HealthcarePublicListingSyncService.class);
        HealthcareDoctorPublicListingChangedEventListener listener = new HealthcareDoctorPublicListingChangedEventListener(syncService);
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        UUID doctorUserId = UUID.fromString("ff4d7d2a-401a-4993-9814-afe2863275b6");
        UUID actorId = UUID.fromString("e9ca829d-69ee-4d1c-8403-4f04a03a6d30");

        listener.handle(HealthcareDoctorPublicListingChangedEvent.changed(
                tenantId,
                doctorUserId,
                doctorUserId.toString(),
                true,
                "PUBLISHED",
                "doctor.profile.updated",
                1754630000000L,
                actorId
        ));

        verify(syncService).syncTenant(tenantId, actorId, "doctor.profile.updated");
    }

    @Test
    void handleIgnoresNullEvents() {
        HealthcarePublicListingSyncService syncService = mock(HealthcarePublicListingSyncService.class);
        HealthcareDoctorPublicListingChangedEventListener listener = new HealthcareDoctorPublicListingChangedEventListener(syncService);

        listener.handle(null);

        verify(syncService, org.mockito.Mockito.never()).syncTenant(any(), any(), any());
    }
}
