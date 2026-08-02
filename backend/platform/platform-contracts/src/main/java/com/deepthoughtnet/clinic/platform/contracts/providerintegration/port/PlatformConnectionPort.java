package com.deepthoughtnet.clinic.platform.contracts.providerintegration.port;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingCapability;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;

import java.util.Optional;

public interface PlatformConnectionPort {
    Optional<BookingTargetResolution> findActivePracticeLink(BookingTargetReference bookingTargetReference);

    BookingCapability resolveBookingCapability(BookingTargetReference bookingTargetReference);

    AvailabilityState resolveAvailabilityState(BookingTargetReference bookingTargetReference);
}
