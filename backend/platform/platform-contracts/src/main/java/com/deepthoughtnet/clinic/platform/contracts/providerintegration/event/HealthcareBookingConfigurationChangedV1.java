package com.deepthoughtnet.clinic.platform.contracts.providerintegration.event;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;

public record HealthcareBookingConfigurationChangedV1(
        BookingTargetReference bookingTargetReference,
        BookingCapability bookingCapability,
        AvailabilityState availabilityState,
        String reason
) implements ProviderIntegrationEventPayload {
}
