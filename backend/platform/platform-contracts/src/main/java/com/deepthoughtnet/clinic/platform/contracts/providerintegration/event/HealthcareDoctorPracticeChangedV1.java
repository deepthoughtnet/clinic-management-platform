package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PlatformConnectionStatus;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.PublicProviderReference;

public record HealthcareDoctorPracticeChangedV1(
        PublicProviderReference doctorReference,
        PublicProviderReference practiceReference,
        String tenantReference,
        PlatformConnectionStatus connectionStatus,
        BookingCapability bookingCapability,
        String reason
) implements ProviderIntegrationEventPayload {
}
