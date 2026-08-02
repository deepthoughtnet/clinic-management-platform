package com.deepthoughtnet.clinic.platform.contracts.providerintegration;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record BookingTargetResolution(
        BookingTargetReference bookingTargetReference,
        ProviderSourceReference sourceReference,
        PublicProfileType publicProfileType,
        PublicProviderReference publicReference,
        String tenantReference,
        String platformClinicReference,
        String tenantDoctorUserReference,
        String tenantDoctorProfileReference,
        BookingCapability bookingCapability,
        AvailabilityState availabilityState,
        PlatformConnectionStatus connectionStatus,
        LinkLifecycleStatus linkStatus,
        long capabilityVersion,
        long connectionRevision,
        OffsetDateTime resolvedAt
) implements Serializable {
}
