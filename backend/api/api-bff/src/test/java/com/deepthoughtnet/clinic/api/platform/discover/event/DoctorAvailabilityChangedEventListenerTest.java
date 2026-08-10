package com.deepthoughtnet.clinic.api.platform.discover.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.deepthoughtnet.clinic.api.platform.discover.HealthcarePublicListingSyncService;
import com.deepthoughtnet.clinic.appointment.events.DoctorAvailabilityChangedEvent;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoctorAvailabilityChangedEventListenerTest {

    @Test
    void handleTriggersTenantReconciliation() {
        HealthcarePublicListingSyncService syncService = mock(HealthcarePublicListingSyncService.class);
        DoctorAvailabilityChangedEventListener listener = new DoctorAvailabilityChangedEventListener(syncService);
        UUID tenantId = UUID.fromString("407dbc68-107d-4f64-83c8-6499e50e5c78");
        UUID availabilityId = UUID.fromString("b5104a50-e5a3-439d-8598-ec5dcc79f3d2");
        UUID doctorUserId = UUID.fromString("a57d88d7-afac-443d-8a03-9f88e2155df6");
        UUID actorId = UUID.fromString("e9ca829d-69ee-4d1c-8403-4f04a03a6d30");

        listener.handle(DoctorAvailabilityChangedEvent.changed(
                tenantId,
                availabilityId,
                doctorUserId,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                true,
                "CREATED",
                "doctor.availability.created",
                actorId
        ));

        verify(syncService).syncTenant(tenantId, actorId, "doctor.availability.created");
    }

    @Test
    void handleIgnoresNullEvents() {
        HealthcarePublicListingSyncService syncService = mock(HealthcarePublicListingSyncService.class);
        DoctorAvailabilityChangedEventListener listener = new DoctorAvailabilityChangedEventListener(syncService);

        listener.handle(null);

        verify(syncService, never()).syncTenant(any(), any(), any());
    }
}
