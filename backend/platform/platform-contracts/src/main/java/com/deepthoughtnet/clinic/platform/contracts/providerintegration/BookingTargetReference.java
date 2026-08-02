package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;

public record BookingTargetReference(
        String opaqueBookingReference,
        long capabilityVersion
) implements Serializable {
}
