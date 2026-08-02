package com.deepthoughtnet.clinic.platform.contracts.providerintegration.port;

import com.deepthoughtnet.clinic.platform.contracts.providerintegration.AvailabilityState;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingSlotSummary;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetResolution;
import com.deepthoughtnet.clinic.platform.contracts.providerintegration.BookingTargetReference;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthcareAvailabilityPort {
    Optional<BookingTargetResolution> resolveBookingTarget(BookingTargetReference bookingTargetReference);

    AvailabilityState getAvailabilityState(BookingTargetReference bookingTargetReference);

    List<BookingSlotSummary> getBookableSlots(BookingTargetReference bookingTargetReference, LocalDate date);
}
